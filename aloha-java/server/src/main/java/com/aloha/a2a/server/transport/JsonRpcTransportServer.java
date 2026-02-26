package com.aloha.a2a.server.transport;

import com.aloha.a2a.server.AgentCardProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.*;
import io.a2a.transport.jsonrpc.handler.JSONRPCHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

/**
 * JSON-RPC transport server using Netty with HTTP POST support.
 * <p>
 * Per A2A spec Section 9 (JSON-RPC Protocol Binding):
 * <ul>
 *   <li>Protocol: JSON-RPC 2.0 over HTTP(S)</li>
 *   <li>Content-Type: application/json for requests and responses</li>
 *   <li>Streaming: Server-Sent Events (text/event-stream)</li>
 *   <li>GET /.well-known/agent-card.json for agent card discovery</li>
 * </ul>
 * <p>
 * SDK v0.3.3 method names: message/send, message/stream, tasks/get, tasks/cancel
 */
public class JsonRpcTransportServer {

    private static final Logger logger = LoggerFactory.getLogger(JsonRpcTransportServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final int port;
    private final AgentCardProvider cardProvider;
    private final AgentExecutor agentExecutor;
    private final Executor executor;

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public JsonRpcTransportServer(int port, AgentCardProvider cardProvider, AgentExecutor agentExecutor) {
        this.port = port;
        this.cardProvider = cardProvider;
        this.agentExecutor = agentExecutor;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void start() throws Exception {
        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore);
        RequestHandler requestHandler = DefaultRequestHandler.create(
                agentExecutor, taskStore, queueManager, null, null, executor);
        AgentCard card = cardProvider.getAgentCard();

        JSONRPCHandler jsonRpcHandler = new JSONRPCHandler(card, requestHandler, executor);
        String cardJson = objectMapper.writeValueAsString(card);

        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("jsonrpc-boss", true));
        workerGroup = new NioEventLoopGroup(0, new DefaultThreadFactory("jsonrpc-worker", true));

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new HttpServerCodec(),
                                new HttpObjectAggregator(65536),
                                new JsonRpcHttpHandler(jsonRpcHandler, cardJson));
                    }
                });

        serverChannel = b.bind(port).sync().channel();
        logger.info("JSON-RPC HTTP server started on port {}", port);
    }

    public void stop() {
        logger.info("Shutting down JSON-RPC transport...");
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        logger.info("JSON-RPC transport stopped");
    }

    public void awaitTermination() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    /**
     * Handles HTTP POST with JSON-RPC 2.0 payloads and GET for agent card.
     * <p>
     * For non-streaming methods (message/send, tasks/get, tasks/cancel):
     *   Request:  POST with Content-Type: application/json, JSON-RPC 2.0 body
     *   Response: Content-Type: application/json, JSON-RPC 2.0 response
     * <p>
     * For streaming method (message/stream):
     *   Request:  POST with Content-Type: application/json, JSON-RPC 2.0 body
     *   Response: Content-Type: text/event-stream, SSE with JSON-RPC 2.0 events
     */
    private static class JsonRpcHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private static final Logger logger = LoggerFactory.getLogger(JsonRpcHttpHandler.class);

        private final JSONRPCHandler jsonRpcHandler;
        private final String cardJson;

        JsonRpcHttpHandler(JSONRPCHandler jsonRpcHandler, String cardJson) {
            this.jsonRpcHandler = jsonRpcHandler;
            this.cardJson = cardJson;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            String uri = request.uri();
            HttpMethod method = request.method();

            // Agent card endpoint
            if ("/.well-known/agent-card.json".equals(uri) && method == HttpMethod.GET) {
                sendJson(ctx, HttpResponseStatus.OK, cardJson);
                return;
            }

            // JSON-RPC 2.0 endpoint: accept POST on any path (SDK posts to agentUrl directly)
            if (method == HttpMethod.POST) {
                handleJsonRpcPost(ctx, request);
                return;
            }

            sendJson(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Not Found\"},\"id\":null}");
        }

        @SuppressWarnings("unchecked")
        private void handleJsonRpcPost(ChannelHandlerContext ctx, FullHttpRequest request) {
            String body = request.content().toString(StandardCharsets.UTF_8);
            logger.debug("Received JSON-RPC request: {}", body);

            try {
                Map<String, Object> jsonRpc = objectMapper.readValue(body, Map.class);
                String rpcMethod = (String) jsonRpc.get("method");
                Object id = jsonRpc.get("id");
                ServerCallContext callContext = new ServerCallContext(null, new java.util.HashMap<>(), java.util.Set.of());

                if (rpcMethod == null) {
                    sendJsonRpcError(ctx, id, -32600, "Invalid Request: missing 'method' field");
                    return;
                }

                logger.info("JSON-RPC method: {}, id: {}", rpcMethod, id);

                switch (rpcMethod) {
                    case "message/send" -> {
                        SendMessageRequest sendReq = objectMapper.convertValue(jsonRpc, SendMessageRequest.class);
                        SendMessageResponse response = jsonRpcHandler.onMessageSend(sendReq, callContext);
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendJson(ctx, HttpResponseStatus.OK, responseJson);
                    }
                    case "message/stream" -> {
                        SendStreamingMessageRequest streamReq = objectMapper.convertValue(jsonRpc, SendStreamingMessageRequest.class);
                        Flow.Publisher<SendStreamingMessageResponse> publisher =
                                jsonRpcHandler.onMessageSendStream(streamReq, callContext);
                        handleSseStream(ctx, publisher);
                    }
                    case "tasks/get" -> {
                        GetTaskRequest getReq = objectMapper.convertValue(jsonRpc, GetTaskRequest.class);
                        GetTaskResponse response = jsonRpcHandler.onGetTask(getReq, callContext);
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendJson(ctx, HttpResponseStatus.OK, responseJson);
                    }
                    case "tasks/cancel" -> {
                        CancelTaskRequest cancelReq = objectMapper.convertValue(jsonRpc, CancelTaskRequest.class);
                        CancelTaskResponse response = jsonRpcHandler.onCancelTask(cancelReq, callContext);
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendJson(ctx, HttpResponseStatus.OK, responseJson);
                    }
                    default -> {
                        logger.warn("Unknown JSON-RPC method: {}", rpcMethod);
                        sendJsonRpcError(ctx, id, -32601, "Method not found: " + rpcMethod);
                    }
                }
            } catch (JSONRPCError e) {
                logger.error("JSON-RPC business error: code={}, message={}", e.getCode(), e.getMessage());
                sendJsonRpcError(ctx, null, e.getCode(), e.getMessage());
            } catch (Exception e) {
                logger.error("Error processing JSON-RPC request: {}", e.getMessage(), e);
                sendJsonRpcError(ctx, null, -32603, "Internal error: " + e.getMessage());
            }
        }

        /**
         * Handles streaming response as Server-Sent Events (SSE).
         * Per spec Section 9.4.2: Response is HTTP 200 with Content-Type: text/event-stream
         * Each event line: data: {JSON-RPC 2.0 response object}\n\n
         */
        private void handleSseStream(ChannelHandlerContext ctx,
                                      Flow.Publisher<SendStreamingMessageResponse> publisher) {
            // Send SSE response headers with chunked transfer encoding
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            ctx.writeAndFlush(response);

            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(SendStreamingMessageResponse item) {
                    try {
                        String json = objectMapper.writeValueAsString(item);
                        String sseEvent = "data: " + json + "\n\n";
                        ctx.writeAndFlush(new DefaultHttpContent(
                                Unpooled.copiedBuffer(sseEvent, StandardCharsets.UTF_8)));
                        logger.debug("SSE event sent ({} bytes)", json.length());
                    } catch (Exception e) {
                        logger.error("Error serializing SSE event: {}", e.getMessage(), e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("SSE stream error: {}", throwable.getMessage(), throwable);
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                            .addListener(ChannelFutureListener.CLOSE);
                }

                @Override
                public void onComplete() {
                    logger.info("SSE stream completed");
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                            .addListener(ChannelFutureListener.CLOSE);
                }
            });
        }

        private void sendJsonRpcError(ChannelHandlerContext ctx, Object id, int code, String message) {
            String safeMessage = message != null ? message.replace("\"", "\\\"") : "Unknown error";
            String idStr = id != null ? (id instanceof String ? "\"" + id + "\"" : id.toString()) : "null";
            String errorJson = String.format(
                    "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":%d,\"message\":\"%s\"},\"id\":%s}",
                    code, safeMessage, idStr);
            sendJson(ctx, HttpResponseStatus.OK, errorJson);
        }

        private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.wrappedBuffer(bytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof java.io.IOException) {
                logger.debug("JSON-RPC connection closed by client: {}", cause.getMessage());
            } else {
                logger.error("JSON-RPC HTTP handler error: {}", cause.getMessage(), cause);
            }
            ctx.close();
        }
    }
}

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
import io.a2a.spec.AgentCard;
import io.a2a.transport.rest.handler.RestHandler;
import io.a2a.transport.rest.handler.RestHandler.HTTPRestResponse;
import io.a2a.transport.rest.handler.RestHandler.HTTPRestStreamingResponse;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST transport server using Netty.
 * <p>
 * Per A2A spec Section 11 (HTTP+JSON/REST Protocol Binding):
 * <ul>
 *   <li>GET  /.well-known/agent-card.json  → agent card discovery</li>
 *   <li>POST /v1/message:send              → send message (JSON response)</li>
 *   <li>POST /v1/message:stream            → send message (SSE response)</li>
 *   <li>GET  /v1/tasks/{id}[?historyLength=n] → get task</li>
 *   <li>POST /v1/tasks/{id}:cancel         → cancel task</li>
 * </ul>
 * <p>
 * SDK v0.3.3 REST client uses protobuf-JSON serialization (JsonFormat.printer());
 * RestHandler handles that internally.
 */
public class RestTransportServer {

    private static final Logger logger = LoggerFactory.getLogger(RestTransportServer.class);
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

    public RestTransportServer(int port, AgentCardProvider cardProvider, AgentExecutor agentExecutor) {
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

        RestHandler restHandler = new RestHandler(card, requestHandler, executor);
        String cardJson = objectMapper.writeValueAsString(card);

        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("rest-boss", true));
        workerGroup = new NioEventLoopGroup(0, new DefaultThreadFactory("rest-worker", true));

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new HttpServerCodec(),
                                new HttpObjectAggregator(65536),
                                new RestHttpHandler(restHandler, cardJson));
                    }
                });

        serverChannel = b.bind(port).sync().channel();
        logger.info("REST HTTP server started on port {}", port);
    }

    public void stop() {
        logger.info("Shutting down REST transport...");
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        logger.info("REST transport stopped");
    }

    public void awaitTermination() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    private static class RestHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private static final Logger logger = LoggerFactory.getLogger(RestHttpHandler.class);

        /**
         * Matches /v1/tasks/{taskId} with optional query string
         */
        private static final Pattern TASKS_GET_PATTERN = Pattern.compile("^/v1/tasks/([^/?]+)(\\?.*)?$");
        /**
         * Matches /v1/tasks/{taskId}:cancel
         */
        private static final Pattern TASKS_CANCEL_PATTERN = Pattern.compile("^/v1/tasks/([^/?]+):cancel$");

        private final RestHandler restHandler;
        private final String cardJson;

        RestHttpHandler(RestHandler restHandler, String cardJson) {
            this.restHandler = restHandler;
            this.cardJson = cardJson;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            String uri = request.uri();
            HttpMethod method = request.method();

            logger.info("REST request: {} {}", method, uri);

            try {
                // Agent card endpoint
                if ("/.well-known/agent-card.json".equals(uri) && method == HttpMethod.GET) {
                    sendJson(ctx, HttpResponseStatus.OK, cardJson);
                    return;
                }

                ServerCallContext callContext = new ServerCallContext(null, new java.util.HashMap<>(), java.util.Set.of());
                String body = request.content().toString(StandardCharsets.UTF_8);

                // Strip query string for path matching
                String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;

                // POST /v1/message:send → send message (JSON response)
                if ("/v1/message:send".equals(path) && method == HttpMethod.POST) {
                    logger.info("Handling message:send");
                    HTTPRestResponse restResp = restHandler.sendMessage(body, callContext);
                    sendResponse(ctx, restResp);
                }
                // POST /v1/message:stream → send message (SSE streaming response)
                else if ("/v1/message:stream".equals(path) && method == HttpMethod.POST) {
                    logger.info("Handling message:stream");
                    HTTPRestResponse restResp = restHandler.sendStreamingMessage(body, callContext);
                    if (restResp instanceof HTTPRestStreamingResponse streamResp) {
                        handleSseStream(ctx, streamResp);
                    } else {
                        // Fallback: non-streaming response from handler
                        sendResponse(ctx, restResp);
                    }
                }
                // GET /v1/tasks/{taskId}[?historyLength=n] → get task
                else if (method == HttpMethod.GET) {
                    Matcher getMatcher = TASKS_GET_PATTERN.matcher(uri);
                    if (getMatcher.matches()) {
                        String taskId = getMatcher.group(1);
                        int historyLength = parseHistoryLength(uri);
                        logger.info("Handling getTask: taskId={}, historyLength={}", taskId, historyLength);
                        HTTPRestResponse restResp = restHandler.getTask(taskId, historyLength, callContext);
                        sendResponse(ctx, restResp);
                    } else {
                        sendJson(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not Found\"}");
                    }
                }
                // POST /v1/tasks/{taskId}:cancel → cancel task
                else if (method == HttpMethod.POST) {
                    Matcher cancelMatcher = TASKS_CANCEL_PATTERN.matcher(path);
                    if (cancelMatcher.matches()) {
                        logger.info("Handling cancelTask");
                        HTTPRestResponse restResp = restHandler.cancelTask(body, callContext);
                        sendResponse(ctx, restResp);
                    } else {
                        sendJson(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not Found\"}");
                    }
                }
                // Not found
                else {
                    sendJson(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not Found\"}");
                }
            } catch (Exception e) {
                logger.error("Error processing REST request {} {}: {}", method, uri, e.getMessage(), e);
                sendJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Internal error") + "\"}");
            }
        }

        /**
         * Parse historyLength query parameter from URI.
         * Example: /v1/tasks/abc123?historyLength=5 → returns 5
         */
        private int parseHistoryLength(String uri) {
            int qIndex = uri.indexOf('?');
            if (qIndex < 0) return 0;
            String query = uri.substring(qIndex + 1);
            for (String param : query.split("&")) {
                if (param.startsWith("historyLength=")) {
                    try {
                        return Integer.parseInt(param.substring("historyLength=".length()));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
            return 0;
        }

        /**
         * Handles streaming response as Server-Sent Events (SSE).
         * Per spec Section 11.7: Content-Type: text/event-stream with
         * data: { StreamResponse object }\n\n
         */
        private void handleSseStream(ChannelHandlerContext ctx, HTTPRestStreamingResponse streamResp) {
            // Send SSE response headers
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            ctx.writeAndFlush(response);

            Flow.Publisher<String> publisher = streamResp.getPublisher();
            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String item) {
                    // Each item is already a JSON string; wrap in SSE format
                    String sseEvent = "data: " + item + "\n\n";
                    ctx.writeAndFlush(new DefaultHttpContent(
                            Unpooled.copiedBuffer(sseEvent, StandardCharsets.UTF_8)));
                    logger.debug("SSE event sent ({} bytes)", item.length());
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

        private void sendResponse(ChannelHandlerContext ctx, HTTPRestResponse restResp) {
            HttpResponseStatus status = HttpResponseStatus.valueOf(restResp.getStatusCode());
            byte[] bytes = restResp.getBody().getBytes(StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.wrappedBuffer(bytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, restResp.getContentType());
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            ctx.writeAndFlush(response);
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
                logger.debug("REST connection closed by client: {}", cause.getMessage());
            } else {
                logger.error("REST handler error: {}", cause.getMessage(), cause);
            }
            ctx.close();
        }
    }
}

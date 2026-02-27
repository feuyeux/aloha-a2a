package com.aloha.a2a.server.transport;

import com.aloha.a2a.server.AgentCardProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.AgentCard;
import io.a2a.transport.grpc.handler.CallContextFactory;
import io.a2a.transport.grpc.handler.GrpcHandler;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * gRPC transport server.
 * <p>
 * Starts:
 * <ul>
 *   <li>gRPC server on {@code grpc.server.port} (default 11000)</li>
 *   <li>Netty HTTP server on {@code http.port} (default 8080) for agent card endpoint</li>
 * </ul>
 */
public class GrpcTransportServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcTransportServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final int grpcPort;
    private final int httpPort;
    private final AgentCardProvider cardProvider;
    private final AgentExecutor agentExecutor;
    private final Executor executor;

    private Server grpcServer;
    private Channel httpChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public GrpcTransportServer(int grpcPort, int httpPort, AgentCardProvider cardProvider, AgentExecutor agentExecutor) {
        this.grpcPort = grpcPort;
        this.httpPort = httpPort;
        this.cardProvider = cardProvider;
        this.agentExecutor = agentExecutor;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void start() throws IOException {
        // Create A2A handler
        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore);
        RequestHandler requestHandler = DefaultRequestHandler.create(
                agentExecutor, taskStore, queueManager, null, null, executor);
        AgentCard card = cardProvider.getAgentCard();

        GrpcHandler handler = new GrpcHandler() {
            @Override
            protected RequestHandler getRequestHandler() {
                return requestHandler;
            }

            @Override
            protected AgentCard getAgentCard() {
                return card;
            }

            @Override
            protected CallContextFactory getCallContextFactory() {
                return new CallContextFactory() {
                    @Override
                    public <V> ServerCallContext create(StreamObserver<V> observer) {
                        return new ServerCallContext(null, new java.util.HashMap<>(), java.util.Set.of());
                    }
                };
            }

            @Override
            protected Executor getExecutor() {
                return executor;
            }
        };

        // Start gRPC server
        grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(handler)
                .build()
                .start();
        logger.info("gRPC server started on port {}", grpcPort);

        // Start HTTP server for agent card (only if httpPort != grpcPort)
        if (httpPort != grpcPort) {
            startHttpServer(card);
        }
    }

    private void startHttpServer(AgentCard card) {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("grpc-http-boss", true));
        workerGroup = new NioEventLoopGroup(0, new DefaultThreadFactory("grpc-http-worker", true));

        try {
            String cardJson = objectMapper.writeValueAsString(card);

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new HttpServerCodec(),
                                    new HttpObjectAggregator(65536),
                                    new AgentCardHttpHandler(cardJson));
                        }
                    });

            httpChannel = b.bind(httpPort).sync().channel();
            logger.info("Agent card HTTP server started on port {}", httpPort);
        } catch (Exception e) {
            logger.error("Failed to start HTTP server for agent card: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        logger.info("Shutting down gRPC transport...");
        if (grpcServer != null) {
            grpcServer.shutdown();
            try {
                grpcServer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (httpChannel != null) {
            httpChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        logger.info("gRPC transport stopped");
    }

    public void awaitTermination() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }

    /**
     * Simple HTTP handler that serves the agent card JSON.
     */
    private static class AgentCardHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final String cardJson;

        AgentCardHttpHandler(String cardJson) {
            this.cardJson = cardJson;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if ("/.well-known/agent-card.json".equals(request.uri()) && request.method() == HttpMethod.GET) {
                byte[] bytes = cardJson.getBytes(StandardCharsets.UTF_8);
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(bytes));
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                ctx.writeAndFlush(response);
            } else {
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
                ctx.writeAndFlush(response);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("HTTP handler error: {}", cause.getMessage(), cause);
            ctx.close();
        }

        private static final Logger logger = LoggerFactory.getLogger(AgentCardHttpHandler.class);
    }
}

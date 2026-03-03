package com.aloha.a2a.server;

import com.aloha.a2a.server.config.AppConfig;
import com.aloha.a2a.server.transport.GrpcTransportServer;
import com.aloha.a2a.server.transport.JsonRpcTransportServer;
import com.aloha.a2a.server.transport.RestTransportServer;
import io.a2a.server.agentexecution.AgentExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application entry point for the Dice Agent.
 * <p>
 * Wires all components manually (no DI framework) and starts the chosen
 * transport server based on the {@code transport.mode} property.
 * <p>
 * Supported transport modes:
 * <ul>
 *   <li>{@code grpc}    – gRPC on {@code grpc.server.port}, agent-card HTTP on {@code http.port}</li>
 *   <li>{@code jsonrpc} – Netty HTTP (JSON-RPC 2.0 over HTTP) on {@code http.port}</li>
 *   <li>{@code rest}    – Netty HTTP on {@code http.port}</li>
 * </ul>
 * <p>
 * Override any property with {@code -Dkey=value} system properties.
 */
public class AlohaServer {

    private static final Logger logger = LoggerFactory.getLogger(AlohaServer.class);

    public static void main(String[] args) {
        // Initialize file logging early (before heavy logger usage)
        // Read transport mode early so we can name the log file
        String earlyMode = System.getProperty("transport.mode", "grpc").toLowerCase();
        initLogFile("java-server-" + earlyMode);

        logger.info("============================================================");
        logger.info("=== Dice Agent starting ===");
        logger.info("============================================================");

        // Start parent-process watchdog so that if the Maven (exec:exec) parent
        // is killed, this forked JVM shuts down automatically instead of
        // becoming an orphan that keeps ports occupied.
        startParentWatchdog();

        // 1. Load configuration
        AppConfig config = new AppConfig();

        String mode = config.getTransportMode();
        logger.info("Server transport: {}", mode.toUpperCase());
        logger.info("Ollama:         {} (model={})", config.getOllamaBaseUrl(), config.getOllamaModel());

        // 2. Wire components (manual DI)
        Tools tools = new Tools();
        DiceAgent agent = new DiceAgent(config, tools);
        DiceAgentExecutor diceExecutor = new DiceAgentExecutor(agent);
        AgentExecutor agentExecutor = diceExecutor.getExecutor();
        AgentCardProvider cardProvider = new AgentCardProvider(config);

        logger.info("Dice Agent initialized");

        // 3. Start transport
        try {
            switch (mode) {
                case "grpc" -> startGrpc(config, cardProvider, agentExecutor);
                case "jsonrpc" -> startJsonRpc(config, cardProvider, agentExecutor);
                case "rest" -> startRest(config, cardProvider, agentExecutor);
                default -> {
                    logger.error("Unknown transport mode: '{}'. Expected grpc, jsonrpc, or rest.", mode);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to start agent: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void startGrpc(AppConfig config, AgentCardProvider cardProvider, AgentExecutor agentExecutor) throws Exception {
        GrpcTransportServer server = new GrpcTransportServer(
                config.getGrpcPort(), config.getHttpPort(), cardProvider, agentExecutor);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping Dice Agent...");
            server.stop();
            logger.info("Dice Agent stopped");
        }, "shutdown-hook"));

        logger.info("============================================================");
        logger.info("Dice Agent is running:");
        logger.info("  - Transport:    GRPC");
        logger.info("  - gRPC endpoint: localhost:{}", config.getGrpcPort());
        logger.info("  - HTTP:         http://localhost:{}", config.getHttpPort());
        logger.info("  - Agent Card:   http://localhost:{}/.well-known/agent-card.json", config.getHttpPort());
        logger.info("============================================================");
        server.awaitTermination();

        // awaitTermination returned — channel was closed. Clean up and force exit
        // in case orphaned non-daemon threads (SDK, gRPC internal) survive.
        logger.info("Dice Agent stopped");
        server.stop();
        System.exit(0);
    }

    private static void startJsonRpc(AppConfig config, AgentCardProvider cardProvider, AgentExecutor agentExecutor) throws Exception {
        JsonRpcTransportServer server = new JsonRpcTransportServer(
                config.getHttpPort(), cardProvider, agentExecutor);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping Dice Agent...");
            server.stop();
            logger.info("Dice Agent stopped");
        }, "shutdown-hook"));

        logger.info("============================================================");
        logger.info("Dice Agent is running:");
        logger.info("  - Transport:    JSON-RPC");
        logger.info("  - JSON-RPC:      http://localhost:{}", config.getHttpPort());
        logger.info("  - Agent Card:    http://localhost:{}/.well-known/agent-card.json", config.getHttpPort());
        logger.info("============================================================");
        server.awaitTermination();

        logger.info("Dice Agent stopped");
        server.stop();
        System.exit(0);
    }

    private static void startRest(AppConfig config, AgentCardProvider cardProvider, AgentExecutor agentExecutor) throws Exception {
        RestTransportServer server = new RestTransportServer(
                config.getRestPort(), cardProvider, agentExecutor);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping Dice Agent...");
            server.stop();
            logger.info("Dice Agent stopped");
        }, "shutdown-hook"));

        logger.info("============================================================");
        logger.info("Dice Agent is running:");
        logger.info("  - Transport:    REST");
        logger.info("  - REST:         http://localhost:{}", config.getRestPort());
        logger.info("  - Agent Card:   http://localhost:{}/.well-known/agent-card.json", config.getRestPort());
        logger.info("============================================================");
        server.awaitTermination();

        logger.info("Dice Agent stopped");
        server.stop();
        System.exit(0);
    }

    /**
     * Monitors ancestor processes and triggers shutdown when any of them
     * terminates. This prevents orphan JVM processes from lingering after
     * {@code mvn exec:exec} is killed on Windows (where child processes are
     * not automatically terminated with their parent).
     * <p>
     * On Windows the process chain is typically:
     * {@code mvn.cmd → java (Maven JVM) → java (server)}. Killing any
     * ancestor (the batch wrapper OR the Maven JVM) will cause this server
     * to self-terminate within 2 seconds.
     * <p>
     * Uses Java 21 {@link ProcessHandle} API.
     * If no parent is found (e.g. started standalone), the watchdog is skipped.
     */
    private static void startParentWatchdog() {
        // Collect the entire ancestor chain (parent, grandparent, …)
        java.util.List<ProcessHandle> ancestors = new java.util.ArrayList<>();
        ProcessHandle current = ProcessHandle.current();
        while (true) {
            var parentOpt = current.parent();
            if (parentOpt.isEmpty()) break;
            ProcessHandle p = parentOpt.get();
            ancestors.add(p);
            current = p;
        }

        if (ancestors.isEmpty()) {
            logger.debug("No parent process found, watchdog skipped");
            return;
        }

        String pids = ancestors.stream()
                .map(p -> String.valueOf(p.pid()))
                .collect(java.util.stream.Collectors.joining(" → "));
        logger.info("Parent process watchdog started (monitoring ancestor PIDs: {})", pids);

        Thread watchdog = new Thread(() -> {
            while (true) {
                for (ProcessHandle ancestor : ancestors) {
                    if (!ancestor.isAlive()) {
                        logger.info("Shutdown signal received, stopping Dice Agent...");
                        System.exit(0);
                    }
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "parent-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Set up file logging by teeing System.err to a log file.
     * SLF4J SimpleLogger writes to System.err by default, so this captures all log output.
     */
    private static void initLogFile(String name) {
        String logDir = System.getProperty("aloha.log.dir",
                System.getenv().getOrDefault("ALOHA_LOG_DIR", "D:\\coding\\aloha-a2a\\aloha-log"));
        try {
            Path dir = Paths.get(logDir);
            Files.createDirectories(dir);
            Path logFile = dir.resolve(name + ".log");
            FileOutputStream fos = new FileOutputStream(logFile.toFile(), true);
            PrintStream tee = new PrintStream(new TeeOutputStream(System.err, fos), true);
            System.setErr(tee);
            logger.info("Log file: {}", logFile);
        } catch (IOException e) {
            System.err.println("WARNING: failed to open log file: " + e.getMessage());
        }
    }

    /**
     * OutputStream that writes to two underlying streams simultaneously.
     */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            out1.write(buf, off, len);
            out2.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }

        @Override
        public void close() throws IOException {
            try { out1.flush(); } catch (IOException ignored) {}
            try { out2.close(); } catch (IOException ignored) {}
        }
    }
}

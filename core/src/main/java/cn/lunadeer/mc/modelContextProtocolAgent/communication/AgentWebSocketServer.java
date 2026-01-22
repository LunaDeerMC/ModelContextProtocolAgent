package cn.lunadeer.mc.modelContextProtocolAgent.communication;

import cn.lunadeer.mc.modelContextProtocolAgent.communication.auth.AuthHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.codec.MessageCodec;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.handler.AuthMessageHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.handler.HeartbeatAckMessageHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.handler.RequestMessageHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.heartbeat.HeartbeatHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.McpMessage;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.GatewaySession;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.WebSocketConnection;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.scheduler.Scheduler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket server for MCP Agent.
 * Handles incoming connections from Gateway.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class AgentWebSocketServer {

    public static class AgentWebSocketServerText extends ConfigurationPart {
        public String wsServerStarted = "MCP Agent WebSocket server started on {0}:{1}";
        public String wsServerFailed = "Failed to start WebSocket server: {0}";
        public String wsBroadcastFailed = "Failed to broadcast to gateway {0}: {1}";
        public String wsConnectionError = "Error handling connection from gateway {0}: {1}";
        public String wsMessageHandlingError = "Failed to handle message from gateway {0}: {1}";
    }

    private final String host;
    private final int port;
    private final SessionManager sessionManager;
    private final MessageCodec messageCodec;
    private final MessageRouter messageRouter;
    private final HeartbeatHandler heartbeatHandler;
    private HttpServer httpServer;
    private boolean running = false;

    public AgentWebSocketServer(String host, int port) {
        this.host = host;
        this.port = port;
        this.sessionManager = new SessionManager(300); // 5 minutes timeout
        this.messageCodec = new MessageCodec();
        this.heartbeatHandler = new HeartbeatHandler(sessionManager);
        this.messageRouter = new MessageRouter();
        initializeMessageHandlers();
    }

    /**
     * Initializes and registers all message handlers.
     */
    private void initializeMessageHandlers() {
        AuthHandler authHandler = new AuthHandler();
        messageRouter.registerHandler(new AuthMessageHandler(authHandler, sessionManager, messageCodec));
        messageRouter.registerHandler(new HeartbeatAckMessageHandler(heartbeatHandler));
        messageRouter.registerHandler(new RequestMessageHandler(messageCodec));
    }

    /**
     * Starts the WebSocket server.
     *
     * @return a CompletableFuture that completes when the server is started
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            try {
                httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
                httpServer.createContext("/ws", new WebSocketUpgradeHandler());
                httpServer.setExecutor(null); // Use default executor
                httpServer.start();
                running = true;

                // Start heartbeat handler
                heartbeatHandler.start();

                XLogger.info(I18n.agentWebSocketServerText.wsServerStarted, host, port);
            } catch (IOException e) {
                XLogger.error(I18n.agentWebSocketServerText.wsServerFailed, e.getMessage());
                throw new RuntimeException("WebSocket server startup failed", e);
            }
        });
    }

    /**
     * Stops the WebSocket server.
     */
    public void stop() {
        if (httpServer != null) {
            running = false;
            sessionManager.closeAllSessions("Server shutdown");
            httpServer.stop(5);
            sessionManager.shutdown();
            XLogger.info("MCP Agent WebSocket server stopped");
        }
    }

    /**
     * Sends a message to a specific session.
     *
     * @param sessionId the session ID
     * @param message   the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> send(String sessionId, McpMessage message) {
        GatewaySession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Session not found: " + sessionId)
            );
        }
        String json = messageCodec.encode(message);
        return session.send(json);
    }

    /**
     * Broadcasts a message to all authenticated sessions.
     *
     * @param message the message to broadcast
     */
    public void broadcast(McpMessage message) {
        String json = messageCodec.encode(message);
        for (GatewaySession session : sessionManager.getAuthenticatedSessions()) {
            session.send(json).exceptionally(ex -> {
                XLogger.warn(I18n.agentWebSocketServerText.wsBroadcastFailed,
                        session.getGatewayId(), ex.getMessage());
                return null;
            });
        }
    }

    /**
     * Gets the session manager.
     *
     * @return the session manager
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Gets the host the server is listening on.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port the server is listening on.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Checks if the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Handles an incoming WebSocket connection.
     */
    private class WebSocketUpgradeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String upgradeHeader = exchange.getRequestHeaders().getFirst("Upgrade");
            if (upgradeHeader == null || !upgradeHeader.equalsIgnoreCase("websocket")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Get Sec-WebSocket-Key from client
            String clientKey = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
            if (clientKey == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Generate Sec-WebSocket-Accept
            String acceptKey = generateWebSocketAcceptKey(clientKey);

            // Generate session ID
            String sessionId = UUID.randomUUID().toString();

            // Create WebSocket connection wrapper
            WebSocketConnectionImpl connection = new WebSocketConnectionImpl(exchange, sessionId);

            // Create gateway session
            GatewaySession session = new GatewaySession(sessionId, connection);
            sessionManager.addSession(session);

            // Send 101 Switching Protocols with proper WebSocket headers
            exchange.getResponseHeaders().add("Connection", "Upgrade");
            exchange.getResponseHeaders().add("Upgrade", "websocket");
            exchange.getResponseHeaders().add("Sec-WebSocket-Accept", acceptKey);
            exchange.sendResponseHeaders(101, -1);

            // Start handling messages in background
            Scheduler.runTaskAsync(() -> handleConnection(session, connection));
        }

        /**
         * Generates the Sec-WebSocket-Accept key from the client's Sec-WebSocket-Key.
         * This follows the WebSocket handshake specification (RFC 6455).
         */
        private String generateWebSocketAcceptKey(String clientKey) {
            try {
                String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                String combined = clientKey + guid;

                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate WebSocket accept key", e);
            }
        }
    }

    /**
     * Handles a WebSocket connection.
     */
    private void handleConnection(GatewaySession session, WebSocketConnectionImpl connection) {
        try {
            // Read messages from the connection
            while (running && connection.isOpen()) {
                String message = connection.receive();
                if (message == null) {
                    break;
                }

                handleMessage(session, message);
            }
        } catch (Exception e) {
            XLogger.error(I18n.agentWebSocketServerText.wsConnectionError,
                    session.getGatewayId(), e.getMessage());
        } finally {
            sessionManager.removeSession(session.getId());
        }
    }

    /**
     * Handles an incoming message.
     */
    private void handleMessage(GatewaySession session, String json) {
        try {
            McpMessage message = messageCodec.decode(json);
            message.setSessionId(session.getId());
            message.setGatewayId(session.getGatewayId());

            // Update last activity time
            session.setLastActivityAt(java.time.Instant.now());

            // Route message to appropriate handler via MessageRouter
            messageRouter.route(session, message);
        } catch (Exception e) {
            XLogger.error(I18n.agentWebSocketServerText.wsMessageHandlingError,
                    session.getGatewayId(), e.getMessage());
        }
    }

    /**
     * WebSocket connection implementation using HttpExchange.
     */
    private static class WebSocketConnectionImpl implements WebSocketConnection {
        private final HttpExchange exchange;
        private volatile boolean open = true;

        public WebSocketConnectionImpl(HttpExchange exchange, String sessionId) {
            this.exchange = exchange;
        }

        @Override
        public CompletableFuture<Void> send(String message) {
            return CompletableFuture.runAsync(() -> {
                try {
                    OutputStream os = exchange.getResponseBody();
                    // Note: This is a simplified implementation
                    // In production, you'd use a proper WebSocket library
                    byte[] bytes = message.getBytes();
                    os.write(bytes);
                    os.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to send message", e);
                }
            });
        }

        @Override
        public void close(int statusCode, String reason) {
            open = false;
            exchange.close();
        }

        public boolean isOpen() {
            return open;
        }

        public String receive() throws IOException {
            // This is a placeholder - proper WebSocket implementation would read from the input stream
            // For now, return null to indicate no message
            return null;
        }
    }
}

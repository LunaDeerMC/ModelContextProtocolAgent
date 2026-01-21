package cn.lunadeer.mc.modelContextProtocolAgent.communication.server;

import cn.lunadeer.mc.modelContextProtocolAgent.communication.auth.AuthHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.auth.AuthResult;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.codec.MessageCodec;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.heartbeat.HeartbeatHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.*;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.GatewaySession;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.WebSocketConnection;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.scheduler.Scheduler;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityManifest;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        public String wsGatewayReauthAttempt = "Gateway {0} attempted re-authentication";
        public String wsUnauthRequestAttempt = "Unauthenticated gateway {0} attempted request";
        public String wsUnknownMessageType = "Unknown message type from gateway {0}: {1}";
    }
    private final String host;
    private final int port;
    private final SessionManager sessionManager;
    private final AuthHandler authHandler;
    private final MessageCodec messageCodec;
    private final HeartbeatHandler heartbeatHandler;
    private HttpServer httpServer;
    private boolean running = false;

    public AgentWebSocketServer(String host, int port) {
        this.host = host;
        this.port = port;
        this.sessionManager = new SessionManager(300); // 5 minutes timeout
        this.authHandler = new AuthHandler();
        this.messageCodec = new MessageCodec();
        this.heartbeatHandler = new HeartbeatHandler(sessionManager);
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
     * @param message the message to send
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

            // Generate session ID
            String sessionId = UUID.randomUUID().toString();

            // Create WebSocket connection wrapper
            WebSocketConnectionImpl connection = new WebSocketConnectionImpl(exchange, sessionId);

            // Create gateway session
            GatewaySession session = new GatewaySession(sessionId, connection);
            sessionManager.addSession(session);

            // Send 101 Switching Protocols
            exchange.getResponseHeaders().add("Connection", "Upgrade");
            exchange.getResponseHeaders().add("Upgrade", "websocket");
            exchange.sendResponseHeaders(101, -1);

            // Start handling messages in background
            Scheduler.runTaskAsync(() -> handleConnection(session, connection));
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

            // Route message based on type
            switch (message.getType()) {
                case "auth":
                    handleAuth(session, (AuthRequest) message);
                    break;
                case "heartbeat_ack":
                    heartbeatHandler.onHeartbeatAck(session.getId(), (HeartbeatAck) message);
                    break;
                case "request":
                    handleRequest(session, (McpRequest) message);
                    break;
                default:
                    XLogger.warn(I18n.agentWebSocketServerText.wsUnknownMessageType,
                            session.getGatewayId(), message.getType());
            }
        } catch (Exception e) {
            XLogger.error(I18n.agentWebSocketServerText.wsMessageHandlingError,
                    session.getGatewayId(), e.getMessage());
        }
    }

    /**
     * Handles an authentication request.
     */
    private void handleAuth(GatewaySession session, AuthRequest request) {
        if (session.isAuthenticated()) {
            XLogger.warn(I18n.agentWebSocketServerText.wsGatewayReauthAttempt, session.getGatewayId());
            return;
        }

        AuthResult result = authHandler.authenticate(request.getGatewayId(), request.getToken());
        if (result.isSuccess()) {
            session.setGatewayId(request.getGatewayId());
            session.setPermissions(result.getPermissions());
            sessionManager.markAuthenticated(session);

            // Send authentication success response with capabilities
            AuthResponse response = AuthResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .success(true)
                    .permissions(result.getPermissions())
                    .capabilities(getCapabilityManifest())
                    .build();

            send(session.getId(), response);
        } else {
            // Send authentication failure response
            AuthResponse response = AuthResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .success(false)
                    .reason(result.getReason())
                    .build();

            send(session.getId(), response);

            // Close the connection
            session.close(4003, "Authentication failed");
        }
    }

    /**
     * Handles a capability request.
     */
    private void handleRequest(GatewaySession session, McpRequest request) {
        if (!session.isAuthenticated()) {
            XLogger.warn(I18n.agentWebSocketServerText.wsUnauthRequestAttempt, session.getGatewayId());
            return;
        }

        // TODO: Implement request handling with execution engine
        // For now, send a placeholder response
        McpResponse response = McpResponse.success(
                request.getId(),
                Map.of("message", "Request received (not yet implemented)")
        ).build();

        send(session.getId(), response);
    }

    /**
     * Gets the capability manifest for the agent.
     * In a real implementation, this would come from the capability registry.
     */
    private List<CapabilityManifest> getCapabilityManifest() {
        // Return empty list for now - this would be populated from the registry
        return Collections.emptyList();
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

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
import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.ExecutionEngine;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket server for MCP Agent using Java-WebSocket library.
 * Handles incoming connections from Gateway.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class AgentWebSocketServer {

    public static class AgentWebSocketServerText extends ConfigurationPart {
        public String wsServerStarted = "MCP Agent WebSocket server for gateway started on {0}:{1}";
        public String wsServerFailed = "Failed to start WebSocket server: {0}";
        public String wsBroadcastFailed = "Failed to broadcast to gateway {0}: {1}";
        public String wsConnectionError = "Error handling connection from gateway {0}: {1}";
        public String wsMessageHandlingError = "Failed to handle message from gateway {0}: {1}";
        public String wsClientConnected = "Client connected from {0}";
        public String wsClientDisconnected = "Client disconnected: {0}, reason: {1}";
    }

    private final String host;
    private final int port;
    private final SessionManager sessionManager;
    private final MessageCodec messageCodec;
    private final MessageRouter messageRouter;
    private final HeartbeatHandler heartbeatHandler;
    private final ExecutionEngine executionEngine;
    private WebSocketServerImpl webSocketServer;
    private boolean running = false;

    public AgentWebSocketServer(String host, int port, ExecutionEngine executionEngine) {
        this.host = host;
        this.port = port;
        this.executionEngine = executionEngine;
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
        messageRouter.registerHandler(new RequestMessageHandler(messageCodec, executionEngine));
    }

    /**
     * Starts the WebSocket server.
     *
     * @return a CompletableFuture that completes when the server is started
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            try {
                InetSocketAddress address = new InetSocketAddress(host, port);
                webSocketServer = new WebSocketServerImpl(address);
                webSocketServer.start();
                running = true;

                // Start heartbeat handler
                heartbeatHandler.start();

                XLogger.info(I18n.agentWebSocketServerText.wsServerStarted, host, port);
            } catch (Exception e) {
                XLogger.error(I18n.agentWebSocketServerText.wsServerFailed, e.getMessage());
                throw new RuntimeException("WebSocket server startup failed", e);
            }
        });
    }

    /**
     * Stops the WebSocket server.
     */
    public void stop() {
        if (webSocketServer != null) {
            running = false;
            sessionManager.closeAllSessions("Server shutdown");
            try {
                webSocketServer.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                XLogger.error("Error stopping WebSocket server: {0}", e.getMessage());
            }
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
     * WebSocket server implementation using Java-WebSocket library.
     */
    private class WebSocketServerImpl extends WebSocketServer {

        public WebSocketServerImpl(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
            String sessionId = UUID.randomUUID().toString();
            XLogger.debug(I18n.agentWebSocketServerText.wsClientConnected,
                    webSocket.getRemoteSocketAddress());

            // Create WebSocket connection wrapper
            WebSocketConnectionImpl connection = new WebSocketConnectionImpl(webSocket);

            // Create gateway session
            GatewaySession session = new GatewaySession(sessionId, connection);
            sessionManager.addSession(session);

            // Store session ID in the WebSocket object for later retrieval
            webSocket.setAttachment(sessionId);
        }

        @Override
        public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
            String sessionId = (String) webSocket.getAttachment();
            XLogger.debug(I18n.agentWebSocketServerText.wsClientDisconnected,
                    webSocket.getRemoteSocketAddress(), reason);

            if (sessionId != null) {
                sessionManager.removeSession(sessionId);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String message) {
            String sessionId = (String) webSocket.getAttachment();
            if (sessionId == null) {
                return;
            }

            GatewaySession session = sessionManager.getSession(sessionId);
            if (session == null) {
                return;
            }

            handleMessage(session, message);
        }

        @Override
        public void onError(WebSocket webSocket, Exception ex) {
            XLogger.error(I18n.agentWebSocketServerText.wsConnectionError,
                    webSocket != null ? webSocket.getRemoteSocketAddress() : "unknown", ex.getMessage());
        }

        @Override
        public void onStart() {
            XLogger.info("WebSocket server started successfully");
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
    }

    /**
     * WebSocket connection implementation using Java-WebSocket.
     */
    private static class WebSocketConnectionImpl implements WebSocketConnection {
        private final WebSocket webSocket;

        public WebSocketConnectionImpl(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public CompletableFuture<Void> send(String message) {
            return CompletableFuture.runAsync(() -> {
                if (webSocket.isOpen()) {
                    webSocket.send(message);
                } else {
                    throw new RuntimeException("WebSocket is not open");
                }
            });
        }

        @Override
        public void close(int statusCode, String reason) {
            webSocket.close(statusCode, reason);
        }
    }
}

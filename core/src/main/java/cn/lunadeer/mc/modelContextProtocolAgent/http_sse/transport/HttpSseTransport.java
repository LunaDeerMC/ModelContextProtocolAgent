package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.transport;

import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler.InitializeHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler.InitializedHandler;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcMessage;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcNotification;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcResponse;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * HTTP SSE (Server-Sent Events) transport for MCP protocol.
 * <p>
 * Implements the HTTP transport specification for MCP, providing:
 * - POST endpoint for sending requests/notifications
 * - GET endpoint for receiving server-sent events
 * - Session management and lifecycle
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class HttpSseTransport {
    
    private static final Gson gson = new Gson();
    private static final String MCP_PATH = "/mcp";
    
    private final String host;
    private final int port;
    private final SessionManager sessionManager;
    private final InitializeHandler initializeHandler;
    private final InitializedHandler initializedHandler;
    private final String bearerToken;
    
    private HttpServer server;
    private ThreadPoolExecutor executor;
    private boolean running = false;
    private final McpHandler mcpHandler;
    
    public HttpSseTransport(
            String host,
            int port,
            SessionManager sessionManager,
            String agentId,
            String agentName,
            String agentVersion,
            String bearerToken
    ) {
        this.host = host;
        this.port = port;
        this.sessionManager = sessionManager;
        this.initializeHandler = new InitializeHandler(sessionManager, agentId, agentName, agentVersion);
        this.initializedHandler = new InitializedHandler(sessionManager);
        this.bearerToken = bearerToken;
        this.mcpHandler = new McpHandler();
    }
    
    /**
     * Starts the HTTP SSE transport server.
     *
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        if (running) {
            return;
        }
        
        // Create thread pool
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10, r -> {
            Thread thread = new Thread(r, "mcp-http-server");
            thread.setDaemon(true);
            return thread;
        });
        
        // Create HTTP server
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(executor);
        
        // Register handlers - both POST and GET on /mcp
        server.createContext(MCP_PATH, mcpHandler);
        
        // Start server
        server.start();
        running = true;
        
        XLogger.info("MCP HTTP SSE server started on http://" + host + ":" + port + MCP_PATH);
    }
    
    /**
     * Stops the HTTP SSE transport server.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        if (server != null) {
            server.stop(0);
        }
        
        if (executor != null) {
            executor.shutdown();
        }
        
        running = false;
        XLogger.info("MCP HTTP SSE server stopped");
    }
    
    /**
     * HTTP handler for MCP requests and SSE.
     */
    private class McpHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String requestMethod = exchange.getRequestMethod().toUpperCase();
                
                // Check bearer token for both GET and POST
                if (!validateAuth(exchange)) {
                    XLogger.warn("MCP Handler: Authentication failed");
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }
                
                // Handle GET requests as SSE connections
                if ("GET".equalsIgnoreCase(requestMethod)) {
                    handleSseConnection(exchange);
                    return;
                }
                
                // Handle POST requests as JSON-RPC messages
                if (!"POST".equalsIgnoreCase(requestMethod)) {
                    XLogger.warn("MCP Handler: Method not allowed: " + requestMethod);
                    sendError(exchange, 405, "Method Not Allowed");
                    return;
                }
                
                // Read request body
                String body = readRequestBody(exchange);
                if (body == null || body.isEmpty()) {
                    XLogger.warn("MCP Handler: Empty request body");
                    sendError(exchange, 400, "Empty request body");
                    return;
                }
                
                XLogger.debug("MCP Handler: Received request body: " + body);
                
                // Parse JSON-RPC message
                JsonRpcMessage message;
                try {
                    message = JsonRpcMessage.fromJson(body);
                    XLogger.debug("MCP Handler: Parsed message type: " + message.getClass().getSimpleName());
                } catch (Exception e) {
                    XLogger.error("MCP Handler: Failed to parse JSON-RPC message: " + e.getMessage(), e);
                    sendError(exchange, 400, "Invalid JSON-RPC message: " + e.getMessage());
                    return;
                }
                
                // Extract session ID from header (optional)
                String sessionId = exchange.getRequestHeaders().getFirst("X-Session-Id");
                
                // Extract protocol version from header (optional)
                String protocolVersion = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
                
                // Handle message based on type
                JsonRpcMessage response;
                if (message.isRequest()) {
                    JsonRpcRequest request = (JsonRpcRequest) message;
                    
                    // For initialize request, try to use request ID as session ID if not provided
                    if ("initialize".equals(request.getMethod())) {
                        if (sessionId == null || sessionId.isEmpty()) {
                            // Generate a session ID based on request ID
                            sessionId = "session-" + request.getId();
                            XLogger.debug("MCP Handler: Generated session ID: " + sessionId + " for initialize request");
                        }
                        if (protocolVersion == null || protocolVersion.isEmpty()) {
                            // Use the protocol version from params if not in header
                            protocolVersion = "2025-11-25"; // Default to supported version
                            XLogger.debug("MCP Handler: Using default protocol version: " + protocolVersion);
                        }
                    } else {
                        // For other requests, use request ID as session ID if not provided
                        if (sessionId == null || sessionId.isEmpty()) {
                            sessionId = "session-" + request.getId();
                        }
                    }
                    
                    response = handleRequest(request, sessionId);
                } else if (message.isNotification()) {
                    // For notifications, generate a session ID if not provided
                    if (sessionId == null || sessionId.isEmpty()) {
                        sessionId = "session-notification-" + System.currentTimeMillis();
                    }
                    
                    handleNotification((JsonRpcNotification) message, sessionId);
                    // Notifications don't have a response
                    sendSuccess(exchange, null);
                    return;
                } else {
                    sendError(exchange, 400, "Invalid message type");
                    return;
                }
                
                // Send response
                if (response != null) {
                    String responseJson = response.toJson();
                    sendSuccess(exchange, responseJson);
                } else {
                    sendSuccess(exchange, null);
                }
            } catch (Exception e) {
                XLogger.error("Error handling MCP request: " + e.getMessage(), e);
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
        
        private void handleSseConnection(HttpExchange exchange) throws IOException {
            try {
                // Set SSE headers
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.getResponseHeaders().set("Connection", "keep-alive");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                
                // Send initial headers
                exchange.sendResponseHeaders(200, 0);
                
                OutputStream os = exchange.getResponseBody();
                
                // Send initial connection event
                String connectedEvent = "event: connected\ndata: {\"status\": \"connected\"}\n\n";
                os.write(connectedEvent.getBytes(StandardCharsets.UTF_8));
                os.flush();
                
                // Keep connection alive (for now, just send keep-alive events)
                // In a full implementation, this would send actual MCP events
                try {
                    while (true) {
                        Thread.sleep(30000); // 30 seconds
                        String keepAlive = ": keep-alive\n\n";
                        os.write(keepAlive.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                XLogger.error("Error handling SSE request: " + e.getMessage(), e);
                try {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.getResponseBody().close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
        
        private JsonRpcResponse handleRequest(JsonRpcRequest request, String sessionId) {
            String method = request.getMethod();
            XLogger.debug("MCP Handler: Processing request method: " + method + ", session: " + sessionId);
            
            if ("initialize".equals(method)) {
                XLogger.debug("MCP Handler: Handling initialize request");
                return initializeHandler.handle(request, sessionId);
            } else {
                XLogger.warn("MCP Handler: Unknown method: " + method);
                // TODO: Handle other MCP methods (tools/call, resources/read, etc.)
                return JsonRpcResponse.createError(
                    request.getId(),
                    -32601,
                    "Method not found: " + method,
                    null
                );
            }
        }
        
        private void handleNotification(JsonRpcNotification notification, String sessionId) {
            String method = notification.getMethod();
            
            if ("notifications/initialized".equals(method)) {
                initializedHandler.handle(notification, sessionId);
            } else {
                XLogger.warn("Unknown notification method: " + method + " for session: " + sessionId);
            }
        }
    }
    

    
    /**
     * Validates the bearer token in the Authorization header.
     */
    private boolean validateAuth(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) {
            return false;
        }
        
        // Check for Bearer token
        if (authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return bearerToken.equals(token);
        }
        
        return false;
    }
    
    /**
     * Reads the request body from the exchange.
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] bytes = is.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Sends a success response.
     */
    private void sendSuccess(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body != null ? body.length() : 0);
        
        if (body != null) {
            OutputStream os = exchange.getResponseBody();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        
        exchange.getResponseBody().close();
    }
    
    /**
     * Sends an error response.
     */
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        
        String body = gson.toJson(error);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, body.length());
        
        OutputStream os = exchange.getResponseBody();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
    }
}

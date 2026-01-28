package cn.lunadeer.mc.modelContextProtocolAgent.http_sse;

import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.transport.HttpSseTransport;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Main MCP HTTP SSE Server.
 * <p>
 * Provides a standard HTTP MCP server for other MCP clients (like Claude Code)
 * to connect to this agent directly.
 * </p>
 * <p>
 * Server lifecycle phases:
 * 1. Initialization - Protocol version negotiation and capability exchange
 * 2. Operation - Normal protocol communication
 * 3. Shutdown - Graceful termination of the connection
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class HttpMcpServer {
    
    private final String host;
    private final int port;
    private final String bearerToken;
    private final String agentId;
    private final String agentName;
    private final String agentVersion;
    
    private SessionManager sessionManager;
    private HttpSseTransport transport;
    private boolean running = false;
    
    /**
     * Constructs a new HttpMcpServer.
     *
     * @param host the host address
     * @param port the port number
     * @param bearerToken the bearer token for authentication
     * @param agentId the agent ID
     * @param agentName the agent name
     * @param agentVersion the agent version
     */
    public HttpMcpServer(
            String host,
            int port,
            String bearerToken,
            String agentId,
            String agentName,
            String agentVersion
    ) {
        this.host = host;
        this.port = port;
        this.bearerToken = bearerToken;
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentVersion = agentVersion;
    }
    
    /**
     * Starts the MCP HTTP server.
     *
     * @return a CompletableFuture that completes when the server is started
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running) {
                XLogger.warn("MCP HTTP server is already running");
                return;
            }
            
            try {
                // Initialize session manager (5 minutes timeout)
                sessionManager = new SessionManager(5 * 60 * 1000);
                sessionManager.start();
                
                // Initialize transport
                transport = new HttpSseTransport(
                    host,
                    port,
                    sessionManager,
                    agentId,
                    agentName,
                    agentVersion,
                    bearerToken
                );
                
                // Start transport
                transport.start();
                
                running = true;
                XLogger.info("MCP HTTP server started successfully");
                
            } catch (IOException e) {
                XLogger.error("Failed to start MCP HTTP server: " + e.getMessage(), e);
                throw new RuntimeException("Failed to start MCP HTTP server", e);
            }
        });
    }
    
    /**
     * Stops the MCP HTTP server.
     *
     * @return a CompletableFuture that completes when the server is stopped
     */
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (!running) {
                XLogger.warn("MCP HTTP server is not running");
                return;
            }
            
            try {
                if (transport != null) {
                    transport.stop();
                }
                
                if (sessionManager != null) {
                    sessionManager.stop();
                }
                
                running = false;
                XLogger.info("MCP HTTP server stopped successfully");
                
            } catch (Exception e) {
                XLogger.error("Error stopping MCP HTTP server: " + e.getMessage(), e);
            }
        });
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
     * Gets the number of active sessions.
     *
     * @return the number of active sessions
     */
    public int getSessionCount() {
        if (sessionManager != null) {
            return sessionManager.getSessionCount();
        }
        return 0;
    }
    
    /**
     * Gets the server host.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Gets the server port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Gets the server URL.
     *
     * @return the server URL
     */
    public String getUrl() {
        return "http://" + host + ":" + port + "/mcp";
    }
}

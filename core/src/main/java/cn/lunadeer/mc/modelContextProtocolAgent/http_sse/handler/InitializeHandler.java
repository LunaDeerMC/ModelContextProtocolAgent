package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionInfo;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionState;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcResponse;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for MCP initialize request.
 * <p>
 * Handles protocol version negotiation and capability exchange.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class InitializeHandler {
    
    private static final Gson gson = new Gson();
    private static final String SUPPORTED_PROTOCOL_VERSION = "2025-11-25";
    
    private final SessionManager sessionManager;
    private final String agentId;
    private final String agentName;
    private final String agentVersion;
    
    public InitializeHandler(SessionManager sessionManager, String agentId, String agentName, String agentVersion) {
        this.sessionManager = sessionManager;
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentVersion = agentVersion;
    }
    
    /**
     * Handles the initialize request.
     *
     * @param request the JSON-RPC request
     * @param sessionId the session ID
     * @return the response
     */
    public JsonRpcResponse handle(JsonRpcRequest request, String sessionId) {
        JsonElement params = request.getParams();
        
        if (params == null || !params.isJsonObject()) {
            return JsonRpcResponse.createError(
                request.getId(),
                -32602, // Invalid params
                "Invalid params: params must be an object",
                null
            );
        }
        
        JsonObject paramsObj = params.getAsJsonObject();
        
        // Extract protocol version
        if (!paramsObj.has("protocolVersion")) {
            return JsonRpcResponse.createError(
                request.getId(),
                -32602,
                "Invalid params: protocolVersion is required",
                null
            );
        }
        
        String clientProtocolVersion = paramsObj.get("protocolVersion").getAsString();
        
        // Extract client capabilities
        if (!paramsObj.has("capabilities")) {
            return JsonRpcResponse.createError(
                request.getId(),
                -32602,
                "Invalid params: capabilities is required",
                null
            );
        }
        
        JsonObject clientCapabilitiesObj = paramsObj.getAsJsonObject("capabilities");
        
        // Extract client info
        if (!paramsObj.has("clientInfo")) {
            return JsonRpcResponse.createError(
                request.getId(),
                -32602,
                "Invalid params: clientInfo is required",
                null
            );
        }
        
        JsonObject clientInfoObj = paramsObj.getAsJsonObject("clientInfo");
        
        // Check protocol version compatibility
        if (!SUPPORTED_PROTOCOL_VERSION.equals(clientProtocolVersion)) {
            JsonObject errorData = new JsonObject();
            errorData.add("supported", gson.toJsonTree(new String[]{SUPPORTED_PROTOCOL_VERSION}));
            errorData.addProperty("requested", clientProtocolVersion);
            
            return JsonRpcResponse.createError(
                request.getId(),
                -32602,
                "Unsupported protocol version",
                errorData
            );
        }
        
        // Get or create session
        SessionInfo session = sessionManager.getSession(sessionId);
        if (session == null) {
            session = sessionManager.createSession(sessionId);
        }
        
        // Set session info
        session.setProtocolVersion(clientProtocolVersion);
        session.setClientCapabilities(gson.fromJson(clientCapabilitiesObj, Map.class));
        session.setClientInfo(gson.fromJson(clientInfoObj, Map.class));
        
        // Build server capabilities
        Map<String, Object> serverCapabilities = buildServerCapabilities();
        
        // Build server info
        Map<String, Object> serverInfo = buildServerInfo();
        
        // Update session with server info
        session.setServerCapabilities(serverCapabilities);
        session.setServerInfo(serverInfo);
        session.setState(SessionState.INITIALIZED);
        
        // Build success response
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", SUPPORTED_PROTOCOL_VERSION);
        result.add("capabilities", gson.toJsonTree(serverCapabilities));
        result.add("serverInfo", gson.toJsonTree(serverInfo));
        result.addProperty("instructions", "MCP Agent ready. Use initialized notification to complete initialization.");
        
        XLogger.debug("MCP session initialized: " + sessionId + 
                     ", client: " + clientInfoObj.get("name").getAsString() +
                     ", protocol: " + clientProtocolVersion);
        
        return JsonRpcResponse.createSuccess(request.getId(), result);
    }
    
    /**
     * Builds the server capabilities object.
     * <p>
     * According to MCP spec, server capabilities include:
     * - prompts: Offers prompt templates
     * - resources: Provides readable resources
     * - tools: Exposes callable tools
     * - logging: Emits structured log messages
     * - completions: Supports argument autocompletion
     * - tasks: Support for task-augmented server requests
     * - experimental: Describes support for non-standard experimental features
     * </p>
     */
    private Map<String, Object> buildServerCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        
        // Tools capability with listChanged support
        Map<String, Object> tools = new HashMap<>();
        tools.put("listChanged", true);
        capabilities.put("tools", tools);
        
        // Resources capability with subscribe and listChanged support
        Map<String, Object> resources = new HashMap<>();
        resources.put("subscribe", true);
        resources.put("listChanged", true);
        capabilities.put("resources", resources);
        
        // Prompts capability with listChanged support
        Map<String, Object> prompts = new HashMap<>();
        prompts.put("listChanged", true);
        capabilities.put("prompts", prompts);
        
        // Logging capability
        Map<String, Object> logging = new HashMap<>();
        capabilities.put("logging", logging);
        
        // Tasks capability
        Map<String, Object> tasks = new HashMap<>();
        Map<String, Object> taskRequests = new HashMap<>();
        Map<String, Object> toolsCall = new HashMap<>();
        taskRequests.put("tools", toolsCall);
        tasks.put("requests", taskRequests);
        capabilities.put("tasks", tasks);
        
        return capabilities;
    }
    
    /**
     * Builds the server info object.
     */
    private Map<String, Object> buildServerInfo() {
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", agentId);
        serverInfo.put("title", agentName);
        serverInfo.put("version", agentVersion);
        serverInfo.put("description", "Minecraft MCP Agent - Provides Minecraft server capabilities via MCP protocol");
        
        return serverInfo;
    }
}

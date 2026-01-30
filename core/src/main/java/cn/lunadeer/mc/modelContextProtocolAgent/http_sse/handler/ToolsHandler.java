package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcResponse;

/**
 * Coordinator for MCP Tools protocol messages.
 * <p>
 * Delegates to specialized handlers for tools/list and tools/call methods.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools#protocol-messages">MCP Tools Protocol Messages</a>
 */
public class ToolsHandler {

    private final ToolsListHandler toolsListHandler;
    private final ToolsCallHandler toolsCallHandler;

    public ToolsHandler(CapabilityRegistry capabilityRegistry) {
        ParameterConverter parameterConverter = new ParameterConverter();
        this.toolsListHandler = new ToolsListHandler(capabilityRegistry);
        this.toolsCallHandler = new ToolsCallHandler(capabilityRegistry, parameterConverter);
    }

    /**
     * Handles tools/list request.
     * <p>
     * Returns a list of available tools.
     * Supports pagination as per MCP specification.
     * </p>
     *
     * @param request the JSON-RPC request
     * @param sessionId the session ID
     * @return the response
     */
    public JsonRpcResponse handleToolsList(JsonRpcRequest request, String sessionId) {
        return toolsListHandler.handleToolsList(request, sessionId);
    }

    /**
     * Handles tools/call request.
     * <p>
     * Invokes a tool with the given arguments.
     * </p>
     *
     * @param request the JSON-RPC request
     * @param sessionId the session ID
     * @return the response
     */
    public JsonRpcResponse handleToolsCall(JsonRpcRequest request, String sessionId) {
        return toolsCallHandler.handleToolsCall(request, sessionId);
    }
}

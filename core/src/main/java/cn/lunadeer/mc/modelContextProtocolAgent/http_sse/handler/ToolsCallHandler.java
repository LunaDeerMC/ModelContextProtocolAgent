package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcResponse;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.McpToolRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.McpToolResult;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.ToolDecorator;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for tools/call requests.
 * <p>
 * Invokes a tool with the given arguments.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class ToolsCallHandler {

    private final CapabilityRegistry capabilityRegistry;
    private final ParameterConverter parameterConverter;

    public ToolsCallHandler(CapabilityRegistry capabilityRegistry, ParameterConverter parameterConverter) {
        this.capabilityRegistry = capabilityRegistry;
        this.parameterConverter = parameterConverter;
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
        try {
            // Extract tool call parameters
            JsonElement params = request.getParams();
            if (params == null || !params.isJsonObject()) {
                return JsonRpcResponse.createError(
                        request.getId(),
                        -32602,
                        "Invalid params: params must be an object",
                        null
                );
            }

            JsonObject paramsObj = params.getAsJsonObject();

            // Validate required fields
            if (!paramsObj.has("name")) {
                return JsonRpcResponse.createError(
                        request.getId(),
                        -32602,
                        "Invalid params: 'name' is required",
                        null
                );
            }

            String toolName = paramsObj.get("name").getAsString();
            JsonObject arguments = paramsObj.has("arguments") ?
                    paramsObj.getAsJsonObject("arguments") : new JsonObject();

            XLogger.debug("ToolsCallHandler: Handling tools/call for tool: " + toolName + ", session: " + sessionId);

            // Get the capability descriptor
            CapabilityDescriptor descriptor = capabilityRegistry.getCapabilityDescriptor(toolName);
            if (descriptor == null) {
                return JsonRpcResponse.createError(
                        request.getId(),
                        -32602,
                        "Unknown tool: " + toolName,
                        null
                );
            }

            // Convert tool request to capability invocation
            McpToolRequest toolRequest = new McpToolRequest(toolName, arguments);
            Map<String, Object> capabilityParams = ToolDecorator.convertToolCallToCapability(toolRequest, descriptor);

            // Invoke the capability
            Object result = invokeCapability(descriptor, capabilityParams, sessionId);

            // Convert capability result to tool result
            McpToolResult toolResult = ToolDecorator.convertCapabilityToToolResult(result, descriptor);

            XLogger.debug("ToolsCallHandler: Tool invocation successful for: " + toolName);

            return JsonRpcResponse.createSuccess(request.getId(), toolResult.toJsonObject());

        } catch (Exception e) {
            XLogger.error("Error handling tools/call: " + e.getMessage(), e);
            XLogger.error(e);

            // Return tool execution error
            McpToolResult errorResult = McpToolResult.error("Tool execution failed: " + e.getMessage());
            return JsonRpcResponse.createSuccess(request.getId(), errorResult.toJsonObject());
        }
    }

    /**
     * Invokes a capability with the given parameters.
     */
    private Object invokeCapability(CapabilityDescriptor descriptor, Map<String, Object> params, String sessionId) throws Exception {
        try {
            Method method = descriptor.getHandlerMethod();
            Object providerInstance = descriptor.getProviderInstance();

            // Prepare parameters for method invocation
            Object[] args = new Object[method.getParameterCount()];
            Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];

                // Get the parameter name from the @Param annotation
                Param paramAnnotation = param.getAnnotation(Param.class);
                String paramName;
                if (paramAnnotation != null && !paramAnnotation.name().isEmpty()) {
                    paramName = paramAnnotation.name();
                } else {
                    // Fallback to the actual parameter name if no annotation or name is empty
                    paramName = param.getName();
                }

                if (params.containsKey(paramName)) {
                    // Convert parameter to expected type
                    Object paramValue = params.get(paramName);
                    args[i] = parameterConverter.convertParameter(paramValue, param);
                } else {
                    // Use default value from @Param annotation if available, otherwise use type-based default
                    if (paramAnnotation != null && !paramAnnotation.defaultValue().isEmpty()) {
                        args[i] = parameterConverter.convertParameter(
                                parameterConverter.parseDefaultValue(paramAnnotation.defaultValue()),
                                param
                        );
                    } else {
                        args[i] = parameterConverter.getDefaultValue(param.getType());
                    }
                }
            }

            // Invoke the method
            Object result = method.invoke(providerInstance, args);

            XLogger.debug("ToolsCallHandler: Capability invoked successfully: " + descriptor.getId());

            return result;

        } catch (Exception e) {
            XLogger.error("Error invoking capability: " + descriptor.getId(), e);
            XLogger.error(e);
            throw new Exception("Failed to invoke capability: " + e.getMessage(), e);
        }
    }
}

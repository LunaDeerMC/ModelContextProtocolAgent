package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.ProviderDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.McpTool;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.McpToolRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.McpToolResult;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.ToolDecorator;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcResponse;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for MCP Tools protocol messages.
 * <p>
 * Implements the tools/list and tools/call methods as per MCP specification.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools#protocol-messages">MCP Tools Protocol Messages</a>
 */
public class ToolsHandler {
    
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    private final CapabilityRegistry capabilityRegistry;
    
    public ToolsHandler(CapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
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
        try {
            // Extract pagination parameters if present
            String cursor = null;
            JsonElement params = request.getParams();
            if (params != null && params.isJsonObject()) {
                JsonObject paramsObj = params.getAsJsonObject();
                if (paramsObj.has("cursor")) {
                    cursor = paramsObj.get("cursor").getAsString();
                }
            }
            
            XLogger.debug("ToolsHandler: Handling tools/list request, session: " + sessionId + ", cursor: " + cursor);
            
            // Get all capabilities (or filter by cursor)
            List<CapabilityDescriptor> descriptors = getDescriptorsForList(cursor);
            
            // Convert to MCP tools
            List<McpTool> tools = ToolDecorator.decorateAll(descriptors);
            
            // Build response
            JsonObject result = new JsonObject();
            JsonArray toolsArray = new JsonArray();
            for (McpTool tool : tools) {
                toolsArray.add(tool.toJsonObject());
            }
            result.add("tools", toolsArray);
            
            // Add nextCursor if pagination is supported
            String nextCursor = getNextCursor(cursor, descriptors.size());
            if (nextCursor != null) {
                result.addProperty("nextCursor", nextCursor);
            }
            
            XLogger.debug("ToolsHandler: Returning " + tools.size() + " tools for session: " + sessionId);
            
            return JsonRpcResponse.createSuccess(request.getId(), result);
            
        } catch (Exception e) {
            XLogger.error("Error handling tools/list: " + e.getMessage(), e);
            return JsonRpcResponse.createError(
                    request.getId(),
                    -32603,
                    "Internal server error: " + e.getMessage(),
                    null
            );
        }
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
            
            XLogger.debug("ToolsHandler: Handling tools/call for tool: " + toolName + ", session: " + sessionId);
            
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
            
            XLogger.debug("ToolsHandler: Tool invocation successful for: " + toolName);
            
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
     * Gets capability descriptors for listing.
     * <p>
     * This method can be extended to support pagination.
     * </p>
     */
    private List<CapabilityDescriptor> getDescriptorsForList(String cursor) {
        // Get all capabilities
        List<CapabilityDescriptor> allDescriptors = new ArrayList<>();
        
        // Get all providers and their capabilities
        capabilityRegistry.getProviderIds().forEach(providerId -> {
            ProviderDescriptor provider = capabilityRegistry.getProviderDescriptor(providerId);
            if (provider != null) {
                provider.getCapabilities().forEach(capability -> {
                    // Only include ACTION & CONTEXT type capabilities as tools
                    if (capability.getType() == CapabilityType.ACTION || capability.getType() == CapabilityType.CONTEXT) {
                        allDescriptors.add(capability);
                    }
                });
            }
        });
        
        // Apply cursor-based pagination if needed
        if (cursor != null && !cursor.isEmpty()) {
            // Simple implementation: cursor is the index
            try {
                int startIndex = Integer.parseInt(cursor);
                if (startIndex < allDescriptors.size()) {
                    return allDescriptors.subList(startIndex, Math.min(startIndex + 50, allDescriptors.size()));
                }
                return new ArrayList<>();
            } catch (NumberFormatException e) {
                // Invalid cursor, return all
                return allDescriptors;
            }
        }
        
        return allDescriptors;
    }
    
    /**
     * Gets the next cursor for pagination.
     */
    private String getNextCursor(String currentCursor, int returnedCount) {
        if (returnedCount < 50) {
            // No more items
            return null;
        }
        
        int currentIndex = 0;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            try {
                currentIndex = Integer.parseInt(currentCursor);
            } catch (NumberFormatException e) {
                currentIndex = 0;
            }
        }
        
        int nextIndex = currentIndex + returnedCount;
        return String.valueOf(nextIndex);
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
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            
            for (int i = 0; i < parameters.length; i++) {
                java.lang.reflect.Parameter param = parameters[i];
                
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
                    args[i] = convertParameter(paramValue, param);
                } else {
                    // Use default value or null
                    args[i] = getDefaultValue(param.getType());
                }
            }
            
            // Invoke the method
            Object result = method.invoke(providerInstance, args);
            
            XLogger.debug("ToolsHandler: Capability invoked successfully: " + descriptor.getId());
            
            return result;
            
        } catch (Exception e) {
            XLogger.error("Error invoking capability: " + descriptor.getId(), e);
            XLogger.error(e);
            throw new Exception("Failed to invoke capability: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts a parameter to the expected type.
     */
    private Object convertParameter(Object value, java.lang.reflect.Parameter parameter) {
        if (value == null) {
            return null;
        }

        Class<?> targetType = parameter.getType();
        Type genericType = parameter.getParameterizedType();

        // Handle enum conversion
        if (targetType.isEnum()) {
            if (value instanceof String) {
                // Convert string to enum
                return Enum.valueOf((Class<? extends Enum>) targetType, (String) value);
            } else if (value instanceof Enum) {
                return value;
            } else {
                // Try to convert to string first
                return Enum.valueOf((Class<? extends Enum>) targetType, value.toString());
            }
        }

        // Handle common conversions
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetType == Float.class || targetType == float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.parseFloat(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        } else if (targetType == JsonObject.class) {
            if (value instanceof JsonObject) {
                return value;
            }
            return gson.toJsonTree(value).getAsJsonObject();
        } else if (targetType == JsonElement.class) {
            return gson.toJsonTree(value);
        } else {
            // Try to convert using Gson with proper type handling
            return convertWithGson(value, targetType, genericType);
        }
    }

    /**
     * Converts a value using Gson with proper type handling for generic types.
     */
    private Object convertWithGson(Object value, Class<?> targetType, Type genericType) {
        // Handle List types with generic parameters
        if (List.class.isAssignableFrom(targetType)) {
            if (value instanceof List) {
                // For List<T>, we need to use TypeToken to preserve generic type info
                // If we have the generic type, use it for proper deserialization
                if (genericType instanceof ParameterizedType) {
                    try {
                        return gson.fromJson(gson.toJson(value), genericType);
                    } catch (Exception e) {
                        XLogger.error("Failed to convert List with generic type: " + e.getMessage(), e);
                    }
                }
                // Fallback: convert each element individually
                List<?> sourceList = (List<?>) value;
                List<Object> result = new ArrayList<>();
                for (Object item : sourceList) {
                    if (item instanceof Map) {
                        // Try to convert Map to the target element type
                        // This is a best-effort conversion
                        result.add(gson.fromJson(gson.toJson(item), Object.class));
                    } else {
                        result.add(item);
                    }
                }
                return result;
            }
        }

        // Handle Map types with generic parameters
        if (Map.class.isAssignableFrom(targetType)) {
            if (value instanceof Map) {
                // For Map<K, V>, we need to use TypeToken to preserve generic type info
                // If we have the generic type, use it for proper deserialization
                if (genericType instanceof ParameterizedType) {
                    try {
                        return gson.fromJson(gson.toJson(value), genericType);
                    } catch (Exception e) {
                        XLogger.error("Failed to convert Map with generic type: " + e.getMessage(), e);
                    }
                }
                // Fallback: convert each entry individually
                Map<?, ?> sourceMap = (Map<?, ?>) value;
                Map<Object, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                    Object key = entry.getKey();
                    Object val = entry.getValue();

                    // Convert key if needed
                    Object convertedKey = key;
                    if (key instanceof String) {
                        // Try to convert to appropriate type
                        convertedKey = key;
                    }

                    // Convert value if needed
                    Object convertedValue = val;
                    if (val instanceof Map) {
                        convertedValue = gson.fromJson(gson.toJson(val), Object.class);
                    }

                    result.put(convertedKey, convertedValue);
                }
                return result;
            }
        }

        // For other complex types (records, POJOs), use Gson's default behavior
        // Gson can handle Java Records with proper configuration
        try {
            return gson.fromJson(gson.toJson(value), targetType);
        } catch (Exception e) {
            XLogger.error("Failed to convert parameter to type " + targetType.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Gets a default value for a parameter type.
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == long.class || type == double.class || type == float.class) {
            return 0;
        } else if (type == boolean.class) {
            return false;
        } else {
            return null;
        }
    }
}

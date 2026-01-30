package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.ProviderDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcResponse;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.McpTool;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.tool.ToolDecorator;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for tools/list requests.
 * <p>
 * Returns a list of available tools with pagination support.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class ToolsListHandler {

    private static final int PAGE_SIZE = 50;

    private final CapabilityRegistry capabilityRegistry;

    public ToolsListHandler(CapabilityRegistry capabilityRegistry) {
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
            String cursor = extractCursor(request);

            XLogger.debug("ToolsListHandler: Handling tools/list request, session: " + sessionId + ", cursor: " + cursor);

            // Get all capabilities (or filter by cursor)
            List<CapabilityDescriptor> descriptors = getDescriptorsForList(cursor);

            // Convert to MCP tools
            List<McpTool> tools = ToolDecorator.decorateAll(descriptors);

            // Build response
            JsonObject result = buildResponse(tools, cursor, descriptors.size());

            XLogger.debug("ToolsListHandler: Returning " + tools.size() + " tools for session: " + sessionId);

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
     * Extracts the cursor from the request parameters.
     */
    private String extractCursor(JsonRpcRequest request) {
        JsonElement params = request.getParams();
        if (params != null && params.isJsonObject()) {
            JsonObject paramsObj = params.getAsJsonObject();
            if (paramsObj.has("cursor")) {
                return paramsObj.get("cursor").getAsString();
            }
        }
        return null;
    }

    /**
     * Builds the response object with tools and pagination info.
     */
    private JsonObject buildResponse(List<McpTool> tools, String cursor, int descriptorCount) {
        JsonObject result = new JsonObject();
        JsonArray toolsArray = new JsonArray();
        for (McpTool tool : tools) {
            toolsArray.add(tool.toJsonObject());
        }
        result.add("tools", toolsArray);

        // Add nextCursor if pagination is supported
        String nextCursor = getNextCursor(cursor, descriptorCount);
        if (nextCursor != null) {
            result.addProperty("nextCursor", nextCursor);
        }

        return result;
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
                    return allDescriptors.subList(startIndex, Math.min(startIndex + PAGE_SIZE, allDescriptors.size()));
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
        if (returnedCount < PAGE_SIZE) {
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
}

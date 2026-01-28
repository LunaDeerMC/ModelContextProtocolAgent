package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Base class for JSON-RPC 2.0 messages used in MCP protocol.
 * <p>
 * Implements the JSON-RPC 2.0 specification for MCP communication.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public abstract class JsonRpcMessage {
    protected static final Gson gson = new Gson();
    
    public static final String JSONRPC_VERSION = "2.0";
    
    private String jsonrpc;
    private Object id;
    private String method;
    private JsonElement params;
    private JsonElement result;
    private JsonObject error;
    
    protected JsonRpcMessage(Object id) {
        this.jsonrpc = JSONRPC_VERSION;
        this.id = id;
    }
    
    protected JsonRpcMessage(Object id, String method) {
        this(id);
        this.method = method;
    }
    
    protected JsonRpcMessage(Object id, JsonElement result) {
        this(id);
        this.result = result;
    }
    
    protected JsonRpcMessage(Object id, JsonObject error) {
        this(id);
        this.error = error;
    }
    
    public String getJsonrpc() {
        return jsonrpc;
    }
    
    public Object getId() {
        return id;
    }
    
    public String getMethod() {
        return method;
    }
    
    public JsonElement getParams() {
        return params;
    }
    
    public void setParams(JsonElement params) {
        this.params = params;
    }
    
    public JsonElement getResult() {
        return result;
    }
    
    public JsonObject getError() {
        return error;
    }
    
    public abstract boolean isRequest();
    
    public abstract boolean isResponse();
    
    public abstract boolean isNotification();
    
    public abstract boolean isError();
    
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("jsonrpc", jsonrpc);
        
        if (id != null) {
            // Handle different types for id (number, string, etc.)
            if (id instanceof Number) {
                json.addProperty("id", (Number) id);
            } else if (id instanceof Boolean) {
                json.addProperty("id", (Boolean) id);
            } else if (id instanceof Character) {
                json.addProperty("id", (Character) id);
            } else {
                json.addProperty("id", id.toString());
            }
        }
        
        if (method != null) {
            json.addProperty("method", method);
        }
        
        if (params != null) {
            json.add("params", params);
        }
        
        if (result != null) {
            json.add("result", result);
        }
        
        if (error != null) {
            json.add("error", error);
        }
        
        return gson.toJson(json);
    }
    
    public static JsonRpcMessage fromJson(String json) {
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
        
        if (jsonObject.has("method")) {
            // It's a request or notification
            Object id = null;
            if (jsonObject.has("id")) {
                JsonElement idElement = jsonObject.get("id");
                if (idElement.isJsonPrimitive()) {
                    if (idElement.getAsJsonPrimitive().isNumber()) {
                        id = idElement.getAsNumber();
                    } else if (idElement.getAsJsonPrimitive().isString()) {
                        id = idElement.getAsString();
                    } else if (idElement.getAsJsonPrimitive().isBoolean()) {
                        id = idElement.getAsBoolean();
                    }
                }
            }
            String method = jsonObject.get("method").getAsString();
            JsonElement params = jsonObject.has("params") ? jsonObject.get("params") : null;
            
            if (id != null) {
                JsonRpcRequest request = new JsonRpcRequest(id, method);
                request.setParams(params);
                return request;
            } else {
                JsonRpcNotification notification = new JsonRpcNotification(method);
                notification.setParams(params);
                return notification;
            }
        } else if (jsonObject.has("result")) {
            // It's a success response
            Object id = null;
            if (jsonObject.has("id")) {
                JsonElement idElement = jsonObject.get("id");
                if (idElement.isJsonPrimitive()) {
                    if (idElement.getAsJsonPrimitive().isNumber()) {
                        id = idElement.getAsNumber();
                    } else if (idElement.getAsJsonPrimitive().isString()) {
                        id = idElement.getAsString();
                    } else if (idElement.getAsJsonPrimitive().isBoolean()) {
                        id = idElement.getAsBoolean();
                    }
                }
            }
            JsonElement result = jsonObject.get("result");
            return new JsonRpcResponse(id, result);
        } else if (jsonObject.has("error")) {
            // It's an error response
            Object id = null;
            if (jsonObject.has("id")) {
                JsonElement idElement = jsonObject.get("id");
                if (idElement.isJsonPrimitive()) {
                    if (idElement.getAsJsonPrimitive().isNumber()) {
                        id = idElement.getAsNumber();
                    } else if (idElement.getAsJsonPrimitive().isString()) {
                        id = idElement.getAsString();
                    } else if (idElement.getAsJsonPrimitive().isBoolean()) {
                        id = idElement.getAsBoolean();
                    }
                }
            }
            JsonObject error = jsonObject.getAsJsonObject("error");
            return new JsonRpcResponse(id, error);
        }
        
        throw new IllegalArgumentException("Invalid JSON-RPC message: " + json);
    }
}

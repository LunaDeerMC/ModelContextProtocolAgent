package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * JSON-RPC 2.0 Response message.
 * <p>
 * A response can be either a success result or an error.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class JsonRpcResponse extends JsonRpcMessage {
    
    private final boolean isError;
    
    public JsonRpcResponse(Object id, JsonElement result) {
        super(id, result);
        this.isError = false;
    }
    
    public JsonRpcResponse(Object id, JsonObject error) {
        super(id, error);
        this.isError = true;
    }
    
    @Override
    public boolean isRequest() {
        return false;
    }
    
    @Override
    public boolean isResponse() {
        return true;
    }
    
    @Override
    public boolean isNotification() {
        return false;
    }
    
    @Override
    public boolean isError() {
        return isError;
    }
    
    /**
     * Creates an error response.
     *
     * @param id the request ID
     * @param code the error code
     * @param message the error message
     * @param data optional error data
     * @return the error response
     */
    public static JsonRpcResponse createError(Object id, int code, String message, JsonElement data) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        if (data != null) {
            error.add("data", data);
        }
        return new JsonRpcResponse(id, error);
    }
    
    /**
     * Creates a success response.
     *
     * @param id the request ID
     * @param result the result
     * @return the success response
     */
    public static JsonRpcResponse createSuccess(Object id, JsonElement result) {
        return new JsonRpcResponse(id, result);
    }
}

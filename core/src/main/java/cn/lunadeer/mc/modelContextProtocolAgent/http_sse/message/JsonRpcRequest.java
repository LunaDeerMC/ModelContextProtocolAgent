package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message;

import com.google.gson.JsonElement;

/**
 * JSON-RPC 2.0 Request message.
 * <p>
 * A request is a method call that expects a response.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class JsonRpcRequest extends JsonRpcMessage {
    
    public JsonRpcRequest(Object id, String method) {
        super(id, method);
    }
    
    @Override
    public boolean isRequest() {
        return true;
    }
    
    @Override
    public boolean isResponse() {
        return false;
    }
    
    @Override
    public boolean isNotification() {
        return false;
    }
    
    @Override
    public boolean isError() {
        return false;
    }
}

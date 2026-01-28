package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message;

/**
 * JSON-RPC 2.0 Notification message.
 * <p>
 * A notification is a method call that does not expect a response.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class JsonRpcNotification extends JsonRpcMessage {
    
    public JsonRpcNotification(String method) {
        super(null, method);
    }
    
    @Override
    public boolean isRequest() {
        return false;
    }
    
    @Override
    public boolean isResponse() {
        return false;
    }
    
    @Override
    public boolean isNotification() {
        return true;
    }
    
    @Override
    public boolean isError() {
        return false;
    }
}

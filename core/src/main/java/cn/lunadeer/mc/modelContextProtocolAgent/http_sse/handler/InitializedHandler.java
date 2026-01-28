package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionInfo;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle.SessionState;
import cn.lunadeer.mc.modelContextProtocolAgent.http_sse.message.JsonRpcNotification;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;

/**
 * Handler for MCP initialized notification.
 * <p>
 * Handles the notification that indicates the client is ready to begin normal operations.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class InitializedHandler {
    
    private final SessionManager sessionManager;
    
    public InitializedHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    /**
     * Handles the initialized notification.
     *
     * @param notification the JSON-RPC notification
     * @param sessionId the session ID
     */
    public void handle(JsonRpcNotification notification, String sessionId) {
        SessionInfo session = sessionManager.getSession(sessionId);
        
        if (session == null) {
            XLogger.warn("Received initialized notification for unknown session: " + sessionId);
            return;
        }
        
        // Check if session is in the correct state
        if (session.getState() != SessionState.INITIALIZED) {
            XLogger.warn("Received initialized notification for session in wrong state: " + 
                        sessionId + ", state: " + session.getState());
            return;
        }
        
        // Transition to operating state
        session.setState(SessionState.OPERATING);
        sessionManager.updateActivity(sessionId);
        
        XLogger.info("MCP session ready for operation: " + sessionId + 
                    ", client: " + (session.getClientInfo() != null ? 
                        session.getClientInfo().get("name") : "unknown"));
    }
}

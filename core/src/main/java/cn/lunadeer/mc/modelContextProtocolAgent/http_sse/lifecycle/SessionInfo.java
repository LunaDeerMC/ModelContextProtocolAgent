package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle;

import java.util.Map;

/**
 * Information about an MCP session.
 * <p>
 * Contains protocol version, capabilities, and client/server information.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class SessionInfo {
    
    private final String sessionId;
    private SessionState state;
    private String protocolVersion;
    private Map<String, Object> clientCapabilities;
    private Map<String, Object> clientInfo;
    private Map<String, Object> serverCapabilities;
    private Map<String, Object> serverInfo;
    private String instructions;
    private long createdAt;
    private long lastActivityAt;
    
    public SessionInfo(String sessionId) {
        this.sessionId = sessionId;
        this.state = SessionState.INITIALIZING;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = this.createdAt;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public SessionState getState() {
        return state;
    }
    
    public void setState(SessionState state) {
        this.state = state;
        updateActivity();
    }
    
    public String getProtocolVersion() {
        return protocolVersion;
    }
    
    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
        updateActivity();
    }
    
    public Map<String, Object> getClientCapabilities() {
        return clientCapabilities;
    }
    
    public void setClientCapabilities(Map<String, Object> clientCapabilities) {
        this.clientCapabilities = clientCapabilities;
        updateActivity();
    }
    
    public Map<String, Object> getClientInfo() {
        return clientInfo;
    }
    
    public void setClientInfo(Map<String, Object> clientInfo) {
        this.clientInfo = clientInfo;
        updateActivity();
    }
    
    public Map<String, Object> getServerCapabilities() {
        return serverCapabilities;
    }
    
    public void setServerCapabilities(Map<String, Object> serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        updateActivity();
    }
    
    public Map<String, Object> getServerInfo() {
        return serverInfo;
    }
    
    public void setServerInfo(Map<String, Object> serverInfo) {
        this.serverInfo = serverInfo;
        updateActivity();
    }
    
    public String getInstructions() {
        return instructions;
    }
    
    public void setInstructions(String instructions) {
        this.instructions = instructions;
        updateActivity();
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getLastActivityAt() {
        return lastActivityAt;
    }
    
    public void updateActivity() {
        this.lastActivityAt = System.currentTimeMillis();
    }
    
    public boolean isExpired(long timeoutMs) {
        return (System.currentTimeMillis() - lastActivityAt) > timeoutMs;
    }
    
    @Override
    public String toString() {
        return "SessionInfo{" +
                "sessionId='" + sessionId + '\'' +
                ", state=" + state +
                ", protocolVersion='" + protocolVersion + '\'' +
                ", createdAt=" + createdAt +
                ", lastActivityAt=" + lastActivityAt +
                '}';
    }
}

package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle;

/**
 * MCP session states according to the protocol specification.
 * <p>
 * The lifecycle consists of three phases:
 * 1. Initialization - Protocol version negotiation and capability exchange
 * 2. Operation - Normal protocol communication
 * 3. Shutdown - Graceful termination of the connection
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public enum SessionState {
    
    /**
     * Initial state - waiting for initialize request.
     */
    INITIALIZING("initializing"),
    
    /**
     * Initialization complete - waiting for initialized notification.
     */
    INITIALIZED("initialized"),
    
    /**
     * Operation phase - normal protocol communication.
     */
    OPERATING("operating"),
    
    /**
     * Shutdown phase - connection is being closed.
     */
    SHUTTING_DOWN("shutting_down"),
    
    /**
     * Session is closed.
     */
    CLOSED("closed");
    
    private final String stateName;
    
    SessionState(String stateName) {
        this.stateName = stateName;
    }
    
    public String getStateName() {
        return stateName;
    }
    
    public static SessionState fromStateName(String stateName) {
        for (SessionState state : SessionState.values()) {
            if (state.stateName.equals(stateName)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown session state: " + stateName);
    }
}

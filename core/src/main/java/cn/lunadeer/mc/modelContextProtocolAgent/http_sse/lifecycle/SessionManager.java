package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.lifecycle;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages MCP sessions for HTTP SSE transport.
 * <p>
 * Handles session lifecycle including initialization, operation, and shutdown.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class SessionManager {
    
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final long sessionTimeoutMs;
    private final ScheduledExecutorService cleanupScheduler;
    private boolean started = false;
    
    public SessionManager(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "mcp-session-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * Starts the session manager.
     */
    public void start() {
        if (!started) {
            cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredSessions,
                1, 1, TimeUnit.MINUTES
            );
            started = true;
            XLogger.info("MCP Session Manager started");
        }
    }
    
    /**
     * Stops the session manager.
     */
    public void stop() {
        if (started) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            sessions.clear();
            started = false;
            XLogger.info("MCP Session Manager stopped");
        }
    }
    
    /**
     * Creates a new session.
     *
     * @param sessionId the session ID
     * @return the created session info
     */
    public SessionInfo createSession(String sessionId) {
        SessionInfo session = new SessionInfo(sessionId);
        sessions.put(sessionId, session);
        XLogger.debug("Created MCP session: " + sessionId);
        return session;
    }
    
    /**
     * Gets a session by ID.
     *
     * @param sessionId the session ID
     * @return the session info, or null if not found
     */
    public SessionInfo getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Removes a session.
     *
     * @param sessionId the session ID
     */
    public void removeSession(String sessionId) {
        SessionInfo removed = sessions.remove(sessionId);
        if (removed != null) {
            XLogger.debug("Removed MCP session: " + sessionId);
        }
    }
    
    /**
     * Updates session activity.
     *
     * @param sessionId the session ID
     */
    public void updateActivity(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session != null) {
            session.updateActivity();
        }
    }
    
    /**
     * Gets the number of active sessions.
     *
     * @return the number of sessions
     */
    public int getSessionCount() {
        return sessions.size();
    }
    
    /**
     * Cleans up expired sessions.
     */
    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(sessionTimeoutMs);
            if (expired) {
                XLogger.debug("Cleaning up expired MCP session: " + entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * Validates that a session is in the correct state for operation.
     *
     * @param sessionId the session ID
     * @return true if the session is ready for operation
     */
    public boolean isSessionReady(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        return session.getState() == SessionState.OPERATING;
    }
}

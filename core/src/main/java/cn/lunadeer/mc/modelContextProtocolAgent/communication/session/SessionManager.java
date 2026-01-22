package cn.lunadeer.mc.modelContextProtocolAgent.communication.session;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages all Gateway connection sessions.
 * Handles session lifecycle, authentication, and cleanup.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class SessionManager {

    public static class SessionManagerText extends ConfigurationPart {
        public String sessionAdded = "Added session {0} for gateway {1}";
        public String sessionRemoved = "Removed session {0} for gateway {1}";
        public String sessionAuthTimeout = "Session {0} authentication timeout, closing connection";
        public String gatewayAuthenticated = "Gateway {0} authenticated successfully with {1} permissions";
        public String closedAllSessions = "Closed all sessions: {0}";
        public String removingStaleSession = "Removing stale session {0} (inactive for {1}s)";
        public String sessionCleanupError = "Error in session cleanup task: {0}";
        public String maxConnectionsReached = "Maximum connections reached ({0}), rejecting gateway {1}";
    }
    private final Map<String, GatewaySession> sessions = new ConcurrentHashMap<>();
    private final Map<String, GatewaySession> authenticatedSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final long sessionTimeoutSeconds;

    public SessionManager(long sessionTimeoutSeconds) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        startSessionCleanupTask();
    }

    /**
     * Adds a new session.
     *
     * @param session the session to add
     */
    public void addSession(GatewaySession session) {
        // Check connection limit
        int maxConnections = cn.lunadeer.mc.modelContextProtocolAgent.Configuration.websocketServer.maxConnections;

        if (maxConnections > 0 && sessions.size() >= maxConnections) {
            XLogger.warn(I18n.sessionManagerText.maxConnectionsReached, maxConnections, session.getGatewayId());
            session.close(4004, "Maximum connections reached");
            return;
        }

        sessions.put(session.getId(), session);
        XLogger.debug(I18n.sessionManagerText.sessionAdded, session.getId(), session.getGatewayId());

        // Schedule authentication timeout check
        scheduler.schedule(() -> {
            if (!session.isAuthenticated()) {
                XLogger.warn(I18n.sessionManagerText.sessionAuthTimeout, session.getId());
                session.close(4002, "Authentication timeout");
                removeSession(session.getId());
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * Removes a session.
     *
     * @param sessionId the session ID to remove
     */
    public void removeSession(String sessionId) {
        GatewaySession session = sessions.remove(sessionId);
        if (session != null && session.getGatewayId() != null) {
            authenticatedSessions.remove(session.getGatewayId());
            XLogger.debug(I18n.sessionManagerText.sessionRemoved, sessionId, session.getGatewayId());
        }
    }

    /**
     * Marks a session as authenticated.
     *
     * @param session the session to mark
     */
    public void markAuthenticated(GatewaySession session) {
        session.setAuthenticated(true);
        authenticatedSessions.put(session.getGatewayId(), session);
        XLogger.info(I18n.sessionManagerText.gatewayAuthenticated, session.getGatewayId(), session.getPermissions().size());
    }

    /**
     * Gets a session by ID.
     *
     * @param sessionId the session ID
     * @return the session or null if not found
     */
    public GatewaySession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Gets a session by Gateway ID.
     *
     * @param gatewayId the gateway ID
     * @return the session or null if not found
     */
    public GatewaySession getSessionByGatewayId(String gatewayId) {
        return authenticatedSessions.get(gatewayId);
    }

    /**
     * Gets all authenticated sessions.
     *
     * @return unmodifiable collection of authenticated sessions
     */
    public Collection<GatewaySession> getAuthenticatedSessions() {
        return Collections.unmodifiableCollection(authenticatedSessions.values());
    }

    /**
     * Gets all sessions (including unauthenticated).
     *
     * @return unmodifiable collection of all sessions
     */
    public Collection<GatewaySession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Gets session statistics.
     *
     * @return session statistics
     */
    public SessionStats getStats() {
        return SessionStats.builder()
                .totalSessions(sessions.size())
                .authenticatedSessions(authenticatedSessions.size())
                .build();
    }

    /**
     * Closes all sessions with the given reason.
     *
     * @param reason the close reason
     */
    public void closeAllSessions(String reason) {
        sessions.values().forEach(session -> {
            session.close(1001, reason);
        });
        sessions.clear();
        authenticatedSessions.clear();
        XLogger.info(I18n.sessionManagerText.closedAllSessions, reason);
    }

    /**
     * Starts the session cleanup task to remove stale sessions.
     */
    private void startSessionCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Instant now = Instant.now();
                sessions.values().removeIf(session -> {
                    if (session.getLastActivityAt() != null) {
                        long inactiveSeconds = java.time.Duration.between(
                                session.getLastActivityAt(), now
                        ).getSeconds();
                        if (inactiveSeconds > sessionTimeoutSeconds) {
                            XLogger.debug(I18n.sessionManagerText.removingStaleSession,
                                    session.getId(), inactiveSeconds);
                            session.close(4000, "Session timeout");
                            if (session.getGatewayId() != null) {
                                authenticatedSessions.remove(session.getGatewayId());
                            }
                            return true;
                        }
                    }
                    return false;
                });
            } catch (Exception e) {
                XLogger.error(I18n.sessionManagerText.sessionCleanupError, e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Shuts down the session manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

package cn.lunadeer.mc.modelContextProtocolAgent.communication.heartbeat;

import cn.lunadeer.mc.modelContextProtocolAgent.Configuration;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.AgentStatus;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.HeartbeatAck;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.HeartbeatMessage;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.GatewaySession;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.scheduler.Scheduler;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles heartbeat monitoring for Gateway connections.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class HeartbeatHandler {

    public static class HeartbeatHandlerText extends ConfigurationPart {
        public String heartbeatHandlerStarted = "Heartbeat handler started (interval: {0}ms, timeout: {1}ms)";
        public String gatewayHeartbeatTimeout = "Gateway {0} heartbeat timeout (elapsed: {1}ms), closing connection";
        public String heartbeatCheckError = "Error in heartbeat check: {0}";
        public String heartbeatSendFailed = "Failed to send heartbeat to gateway {0}: {1}";
        public String heartbeatSendError = "Error sending heartbeat to gateway {0}: {1}";
        public String heartbeatAckReceived = "Received heartbeat ack from gateway {0}";
        public String heartbeatRetryExceeded = "Gateway {0} exceeded max retries ({1}), closing connection";
    }
    private final SessionManager sessionManager;
    private final long intervalMs;
    private final long timeoutMs;
    private final long reconnectDelayMs;
    private final int maxRetries;

    public HeartbeatHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.intervalMs = Configuration.websocketServer.heartbeatInterval;
        this.timeoutMs = Configuration.websocketServer.heartbeatTimeout;
        this.reconnectDelayMs = Configuration.websocketServer.reconnectDelay;
        this.maxRetries = Configuration.websocketServer.maxRetries;
    }

    /**
     * Starts the heartbeat monitoring.
     */
    public void start() {
        long intervalTicks = intervalMs / 50; // Convert ms to ticks
        long initialDelayTicks = intervalTicks;

        Scheduler.runTaskRepeatAsync(this::checkAllSessions, initialDelayTicks, intervalTicks);
        XLogger.info(I18n.heartbeatHandlerText.heartbeatHandlerStarted, intervalMs, timeoutMs);
    }

    /**
     * Checks all sessions for heartbeat status.
     */
    private void checkAllSessions() {
        try {
            Instant now = Instant.now();

            for (GatewaySession session : sessionManager.getAuthenticatedSessions()) {
                // Check if last heartbeat response is too old
                if (session.getLastHeartbeatAt() != null) {
                    long elapsedMs = java.time.Duration.between(
                            session.getLastHeartbeatAt(), now
                    ).toMillis();

                    if (elapsedMs > timeoutMs) {
                        session.incrementFailedHeartbeatCount();
                        int failedCount = session.getFailedHeartbeatCount();

                        if (failedCount > maxRetries) {
                            XLogger.warn(I18n.heartbeatHandlerText.heartbeatRetryExceeded,
                                    session.getGatewayId(), maxRetries);
                            session.close(4000, "Heartbeat timeout exceeded max retries");
                            sessionManager.removeSession(session.getId());
                            continue;
                        }

                        XLogger.warn(I18n.heartbeatHandlerText.gatewayHeartbeatTimeout,
                                session.getGatewayId(), elapsedMs);
                    }
                }

                // Send heartbeat request
                sendHeartbeat(session);
            }
        } catch (Exception e) {
            XLogger.error(I18n.heartbeatHandlerText.heartbeatCheckError, e.getMessage());
        }
    }

    /**
     * Sends a heartbeat to a session.
     *
     * @param session the session to send heartbeat to
     */
    private void sendHeartbeat(GatewaySession session) {
        try {
            HeartbeatMessage heartbeat = HeartbeatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .gatewayId(session.getGatewayId())
                    .timestamp(Instant.now())
                    .status(buildStatus())
                    .build();

            session.send(heartbeat.toString()).exceptionally(ex -> {
                XLogger.error(I18n.heartbeatHandlerText.heartbeatSendFailed,
                        session.getGatewayId(), ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            XLogger.error(I18n.heartbeatHandlerText.heartbeatSendError,
                    session.getGatewayId(), e.getMessage());
        }
    }

    /**
     * Handles a heartbeat acknowledgment from a Gateway.
     *
     * @param sessionId the session ID
     * @param ack the heartbeat acknowledgment
     */
    public void onHeartbeatAck(String sessionId, HeartbeatAck ack) {
        GatewaySession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.setLastHeartbeatAt(Instant.now());
            session.setFailedHeartbeatCount(0); // Reset retry counter on successful heartbeat
            XLogger.debug(I18n.heartbeatHandlerText.heartbeatAckReceived, session.getGatewayId());
        }
    }

    /**
     * Builds the agent status for heartbeat messages.
     *
     * @return the agent status
     */
    private AgentStatus buildStatus() {
        double[] tps = Bukkit.getTPS();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsage = (double) usedMemory / maxMemory * 100;

        return AgentStatus.builder()
                .healthy(true)
                .tps(tps.length > 0 ? tps[0] : 20.0)
                .onlinePlayers(onlinePlayers)
                .memoryUsage(memoryUsage)
                .connectedGateways(sessionManager.getAuthenticatedSessions().size())
                .build();
    }
}

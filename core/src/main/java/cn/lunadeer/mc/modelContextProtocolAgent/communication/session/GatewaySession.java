package cn.lunadeer.mc.modelContextProtocolAgent.communication.session;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a Gateway connection session.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class GatewaySession {

    public static class GatewaySessionText extends ConfigurationPart {
        public String closingSession = "Closing session {0} with code {1}: {2}";
    }
    private final String id;
    private final WebSocketConnection connection;
    private final Instant connectedAt;

    private volatile boolean authenticated = false;
    private volatile String gatewayId;
    private volatile Set<String> permissions;
    private volatile Instant lastActivityAt;
    private volatile Instant lastHeartbeatAt;

    public GatewaySession(String id, WebSocketConnection connection) {
        this.id = id;
        this.connection = connection;
        this.connectedAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    /**
     * Sends a message to the gateway.
     *
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> send(String message) {
        lastActivityAt = Instant.now();
        return connection.send(message);
    }

    /**
     * Closes the session.
     *
     * @param statusCode the WebSocket close status code
     * @param reason the close reason
     */
    public void close(int statusCode, String reason) {
        XLogger.debug(I18n.gatewaySessionText.closingSession, id, statusCode, reason);
        connection.close(statusCode, reason);
    }

    /**
     * Checks if the session has a specific permission.
     *
     * @param permission the permission to check
     * @return true if the session has the permission
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Checks if the session has any of the specified permissions.
     *
     * @param permissions the permissions to check
     * @return true if the session has at least one of the permissions
     */
    public boolean hasAnyPermission(Set<String> permissions) {
        if (this.permissions == null || permissions == null) {
            return false;
        }
        return this.permissions.stream().anyMatch(permissions::contains);
    }

    @Override
    public String toString() {
        return String.format("GatewaySession{id='%s', gatewayId='%s', authenticated=%s}",
                id, gatewayId, authenticated);
    }
}

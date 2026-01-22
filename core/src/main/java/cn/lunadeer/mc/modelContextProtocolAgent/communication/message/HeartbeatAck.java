package cn.lunadeer.mc.modelContextProtocolAgent.communication.message;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;

/**
 * Heartbeat acknowledgment message (Gateway -> Agent).
 * Gateway responds to Agent's heartbeat to confirm connection.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class HeartbeatAck extends McpMessage {
    private final String gatewayId;
    private final Instant timestamp;

    private HeartbeatAck(Builder builder) {
        super(builder.id, "heartbeat_ack");
        this.gatewayId = builder.gatewayId;
        this.timestamp = builder.timestamp;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public JsonElement getPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("gatewayId", gatewayId);
        payload.addProperty("timestamp", timestamp.toString());
        return payload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String gatewayId;
        private Instant timestamp;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder gatewayId(String gatewayId) {
            this.gatewayId = gatewayId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public HeartbeatAck build() {
            return new HeartbeatAck(this);
        }
    }
}

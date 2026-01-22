package cn.lunadeer.mc.modelContextProtocolAgent.communication.codec;

import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.*;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import com.google.gson.*;

import java.time.Instant;

/**
 * Message codec for encoding and decoding MCP messages.
 * Handles serialization between JSON and message objects.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class MessageCodec {

    public static class MessageCodecText extends ConfigurationPart {
        public String codecEncodeFailed = "Failed to encode message: {0}";
        public String codecDecodeJsonFailed = "Failed to decode JSON: {0}";
        public String codecDecodeMessageFailed = "Failed to decode message: {0}";
    }

    private final Gson gson;

    public MessageCodec() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();
    }

    /**
     * Encodes a message to JSON string.
     *
     * @param message the message to encode
     * @return JSON string representation
     */
    public String encode(McpMessage message) {
        try {
            MessageFrame frame = MessageFrame.builder()
                    .id(message.getId())
                    .type(message.getType())
                    .timestamp(Instant.now())
                    .correlationId(message.getCorrelationId())
                    .payload(message.getPayload())
                    .build();
            return gson.toJson(frame);
        } catch (Exception e) {
            XLogger.error(I18n.messageCodecText.codecEncodeFailed, e.getMessage());
            throw new CodecException("Message encoding failed", e);
        }
    }

    /**
     * Decodes a JSON string to a message object.
     *
     * @param json the JSON string to decode
     * @return the decoded message
     */
    public McpMessage decode(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            String type = jsonObject.get("type").getAsString();
            String id = jsonObject.get("id").getAsString();
            JsonElement payload = jsonObject.get("payload");
            String correlationId = null;
            if (jsonObject.has("correlationId") && !jsonObject.get("correlationId").isJsonNull()) {
                correlationId = jsonObject.get("correlationId").getAsString();
            }

            McpMessage message = decodeMessage(type, id, payload);
            if (message != null && correlationId != null) {
                message.setCorrelationId(correlationId);
            }
            return message;
        } catch (JsonSyntaxException e) {
            XLogger.error(I18n.messageCodecText.codecDecodeJsonFailed, e.getMessage());
            throw new CodecException("Invalid JSON format", e);
        } catch (Exception e) {
            XLogger.error(I18n.messageCodecText.codecDecodeMessageFailed, e.getMessage());
            throw new CodecException("Message decoding failed", e);
        }
    }

    /**
     * Decodes the message based on type.
     */
    private McpMessage decodeMessage(String type, String id, JsonElement payload) {
        switch (type) {
            case "request":
                return decodeRequest(id, payload);
            case "response":
                return decodeResponse(id, payload);
            case "event":
                return decodeEvent(id, payload);
            case "heartbeat":
                return decodeHeartbeat(id, payload);
            case "heartbeat_ack":
                return decodeHeartbeatAck(id, payload);
            case "auth":
                return decodeAuthRequest(id, payload);
            case "auth_response":
                return decodeAuthResponse(id, payload);
            default:
                throw new CodecException("Unknown message type: " + type);
        }
    }

    private McpRequest decodeRequest(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        String capabilityId = obj.get("capabilityId").getAsString();
        String callerId = obj.has("callerId") ? obj.get("callerId").getAsString() : null;

        McpRequest.Builder builder = McpRequest.builder()
                .id(id)
                .capabilityId(capabilityId)
                .callerId(callerId);

        if (obj.has("parameters")) {
            JsonObject params = obj.getAsJsonObject("parameters");
            for (var entry : params.entrySet()) {
                builder.parameter(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

    private McpResponse decodeResponse(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        boolean success = obj.get("success").getAsBoolean();

        McpResponse.Builder builder = McpResponse.builder()
                .id(id)
                .success(success);

        if (!success) {
            if (obj.has("errorCode")) {
                // Note: We'll need to handle ErrorCode conversion properly
                builder.errorMessage(obj.has("errorMessage") ? obj.get("errorMessage").getAsString() : null);
            }
            if (obj.has("details")) {
                // Parse details as needed
            }
        } else if (obj.has("data")) {
            // Parse data as needed
        }

        return builder.build();
    }

    private McpEvent decodeEvent(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        String eventId = obj.get("eventId").getAsString();
        Object eventData = null;
        if (obj.has("eventData")) {
            eventData = gson.fromJson(obj.get("eventData"), Object.class);
        }

        return McpEvent.builder()
                .id(id)
                .eventId(eventId)
                .eventData(eventData)
                .build();
    }

    private HeartbeatMessage decodeHeartbeat(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        String gatewayId = obj.get("gatewayId").getAsString();
        Instant timestamp = Instant.parse(obj.get("timestamp").getAsString());

        AgentStatus status = null;
        if (obj.has("status")) {
            JsonObject statusObj = obj.getAsJsonObject("status");
            status = AgentStatus.builder()
                    .healthy(statusObj.get("healthy").getAsBoolean())
                    .tps(statusObj.get("tps").getAsDouble())
                    .onlinePlayers(statusObj.get("onlinePlayers").getAsInt())
                    .memoryUsage(statusObj.get("memoryUsage").getAsDouble())
                    .connectedGateways(statusObj.get("connectedGateways").getAsInt())
                    .build();
        }

        return HeartbeatMessage.builder()
                .id(id)
                .gatewayId(gatewayId)
                .timestamp(timestamp)
                .status(status)
                .build();
    }

    private HeartbeatAck decodeHeartbeatAck(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        String gatewayId = obj.get("gatewayId").getAsString();
        Instant timestamp = Instant.parse(obj.get("timestamp").getAsString());

        return HeartbeatAck.builder()
                .id(id)
                .gatewayId(gatewayId)
                .timestamp(timestamp)
                .build();
    }

    private AuthRequest decodeAuthRequest(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        String gatewayId = obj.get("gatewayId").getAsString();
        String token = obj.get("token").getAsString();

        return AuthRequest.builder()
                .id(id)
                .gatewayId(gatewayId)
                .token(token)
                .build();
    }

    private AuthResponse decodeAuthResponse(String id, JsonElement payload) {
        JsonObject obj = payload.getAsJsonObject();
        boolean success = obj.get("success").getAsBoolean();
        String reason = obj.has("reason") ? obj.get("reason").getAsString() : null;

        AuthResponse.Builder builder = AuthResponse.builder()
                .id(id)
                .success(success)
                .reason(reason);

        // Note: permissions and capabilities would need proper deserialization
        // This is a simplified version

        return builder.build();
    }
}

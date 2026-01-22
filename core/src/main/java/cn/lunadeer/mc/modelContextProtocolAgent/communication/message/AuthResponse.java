package cn.lunadeer.mc.modelContextProtocolAgent.communication.message;

import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityManifest;
import com.google.gson.*;

import java.util.List;
import java.util.Set;

/**
 * Authentication response message (Agent -> Gateway).
 * This is the response to AuthRequest from Gateway.
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class AuthResponse extends McpMessage {
    private final boolean success;
    private final String gatewayId;
    private final String sessionId;
    private final AgentInfo agentInfo;
    private final String reason;
    private final Set<String> permissions;
    private final List<CapabilityManifest> capabilities;
    private final Config config;

    private AuthResponse(Builder builder) {
        super(builder.id, "register_ack");
        this.success = builder.success;
        this.gatewayId = builder.gatewayId;
        this.sessionId = builder.sessionId;
        this.agentInfo = builder.agentInfo;
        this.reason = builder.reason;
        this.permissions = builder.permissions;
        this.capabilities = builder.capabilities;
        this.config = builder.config;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public AgentInfo getAgentInfo() {
        return agentInfo;
    }

    public String getReason() {
        return reason;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public List<CapabilityManifest> getCapabilities() {
        return capabilities;
    }

    public Config getConfig() {
        return config;
    }

    @Override
    public JsonElement getPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("success", success);
        payload.addProperty("gatewayId", gatewayId);
        payload.addProperty("sessionId", sessionId);

        if (success) {
            if (agentInfo != null) {
                JsonObject agentInfoObj = new JsonObject();
                agentInfoObj.addProperty("id", agentInfo.getId());
                agentInfoObj.addProperty("name", agentInfo.getName());
                agentInfoObj.addProperty("version", agentInfo.getVersion());
                agentInfoObj.addProperty("environment", agentInfo.getEnvironment());
                
                if (agentInfo.getServerInfo() != null) {
                    JsonObject serverInfoObj = new JsonObject();
                    serverInfoObj.addProperty("name", agentInfo.getServerInfo().getName());
                    serverInfoObj.addProperty("type", agentInfo.getServerInfo().getType());
                    serverInfoObj.addProperty("version", agentInfo.getServerInfo().getVersion());
                    serverInfoObj.addProperty("maxPlayers", agentInfo.getServerInfo().getMaxPlayers());
                    agentInfoObj.add("serverInfo", serverInfoObj);
                }
                payload.add("agentInfo", agentInfoObj);
            }
            
            if (config != null) {
                JsonObject configObj = new JsonObject();
                configObj.addProperty("heartbeatInterval", config.getHeartbeatInterval());
                configObj.addProperty("reconnectDelay", config.getReconnectDelay());
                configObj.addProperty("maxRetries", config.getMaxRetries());
                payload.add("config", configObj);
            }

            if (capabilities != null && !capabilities.isEmpty()) {
                JsonArray capsArray = new JsonArray();
                Gson gson = new GsonBuilder().create();
                for (CapabilityManifest cap : capabilities) {
                    capsArray.add(gson.toJsonTree(cap));
                }
                payload.add("capabilities", capsArray);
            }
        } else {
            if (reason != null) {
                payload.addProperty("reason", reason);
            }
        }

        if (permissions != null && !permissions.isEmpty()) {
            JsonArray permsArray = new JsonArray();
            for (String perm : permissions) {
                permsArray.add(perm);
            }
            payload.add("permissions", permsArray);
        }

        return payload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private boolean success = true;
        private String gatewayId;
        private String sessionId;
        private AgentInfo agentInfo;
        private String reason;
        private Set<String> permissions;
        private List<CapabilityManifest> capabilities;
        private Config config;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder gatewayId(String gatewayId) {
            this.gatewayId = gatewayId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder agentInfo(AgentInfo agentInfo) {
            this.agentInfo = agentInfo;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder capabilities(List<CapabilityManifest> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        public AuthResponse build() {
            return new AuthResponse(this);
        }
    }

    public static class AgentInfo {
        private final String id;
        private final String name;
        private final String version;
        private final String environment;
        private final ServerInfo serverInfo;

        public AgentInfo(String id, String name, String version, String environment, ServerInfo serverInfo) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.environment = environment;
            this.serverInfo = serverInfo;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getEnvironment() {
            return environment;
        }

        public ServerInfo getServerInfo() {
            return serverInfo;
        }
    }

    public static class ServerInfo {
        private final String name;
        private final String type;
        private final String version;
        private final int maxPlayers;

        public ServerInfo(String name, String type, String version, int maxPlayers) {
            this.name = name;
            this.type = type;
            this.version = version;
            this.maxPlayers = maxPlayers;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getVersion() {
            return version;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }
    }

    public static class Config {
        private final int heartbeatInterval;
        private final int reconnectDelay;
        private final int maxRetries;

        public Config(int heartbeatInterval, int reconnectDelay, int maxRetries) {
            this.heartbeatInterval = heartbeatInterval;
            this.reconnectDelay = reconnectDelay;
            this.maxRetries = maxRetries;
        }

        public int getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public int getReconnectDelay() {
            return reconnectDelay;
        }

        public int getMaxRetries() {
            return maxRetries;
        }
    }
}

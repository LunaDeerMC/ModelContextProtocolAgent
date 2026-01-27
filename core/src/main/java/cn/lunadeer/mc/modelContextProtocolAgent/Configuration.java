package cn.lunadeer.mc.modelContextProtocolAgent;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuration extends ConfigurationFile {

    @Comment("Language code for messages. The supported language codes can be found in the 'languages' folder.")
    public static String language = "en_US";

    public static class AgentInfo extends ConfigurationPart {
        @Comment("Unique identifier for this MCP Agent instance.")
        public String agentId = "mcp-agent-default";

        @Comment("Display name for this MCP Agent instance.")
        public String agentName = "MCP Agent";

        @Comment("Version of this MCP Agent instance.")
        public String agentVersion = "1.0.0";

        @Comment("Environment (production, development, staging).")
        public String environment = "production";
    }

    @Comment("Information about this MCP Agent.")
    public static AgentInfo agentInfo = new AgentInfo();

    @Comments({
            "Permissions granted to connected gateways.",
            "Each gateway can request capabilities based on these permissions.",
            "The key is the gateway ID, and the value is the list of permissions.",
            "The 'default' key will be used for gateways not explicitly listed."
    })
    public static HashMap<String, List<String>> gatewayPermissions = new HashMap<>(
            Map.of(
                    "default", List.of(
                            "mcp.capability.execution",
                            "mcp.capability.event-emitter",
                            "mcp.capability.command-manager"
                    )
            )
    );

    public static class WebsocketServer extends ConfigurationPart {
        @Comment("Enable or disable the websocket server on start.")
        public boolean enableOnStart = true;

        @Comment("Host address for websocket server.")
        public String host = "127.0.0.1";

        @Comment("Port for websocket server.")
        public int port = 8765;

        @Comment("Secret key for connection.")
        public String authToken = "ChangeMe!";

        @Comment("Heartbeat interval in milliseconds.")
        public int heartbeatInterval = 30000;

        @Comment("Heartbeat timeout in milliseconds.")
        public int heartbeatTimeout = 90000;

        @Comment("Reconnect delay in milliseconds.")
        public int reconnectDelay = 5000;

        @Comment("Maximum number of retries for failed operations.")
        public int maxRetries = 3;

        @Comment("Maximum number of gateway connections.")
        public int maxConnections = 1;
    }

    @Comment("Websocket server for gateway to connect.")
    public static WebsocketServer websocketServer = new WebsocketServer();

    public static class InternalModelContextProtocolServer extends ConfigurationPart {
        @Comment("Enable or disable the internal MCP server on start.")
        public boolean enableOnStart = false;

        @Comment("Host address for internal MCP server.")
        public String host = "0.0.0.0";

        @Comment("Port for internal MCP server.")
        public int port = 8766;

        @Comment("Bearer token for internal MCP server authentication.")
        public String bearerToken = "ChangeMeToo!";
    }

    @Comments({
            "Internal MCP server configuration.",
            "Provides an basic MCP server for other MCP clients (like Claude Code) to connect to this agent directly.",
            "Server will start on http://<host>:<port>/mcp",
            "Connecting clients must provide the bearer token in the 'Authorization' header."
    })
    public static InternalModelContextProtocolServer internalModelContextProtocolServer = new InternalModelContextProtocolServer();

    @Comment("Enable or disable debug mode.")
    public static boolean debug = false;

    @PostProcess
    public void postProcess() {
        // Ensure default gateway permissions exist
        if (!gatewayPermissions.containsKey("default")) {
            XLogger.warn("No default gateway permissions configured, adding default permissions.");
            gatewayPermissions.put("default", List.of(
                    "mcp.capability.execution",
                    "mcp.capability.event-emitter",
                    "mcp.capability.command-manager"
            ));
        }
    }

}

package cn.lunadeer.mc.modelContextProtocolAgent;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.Comment;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationFile;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;

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

    public static class WebsocketServer extends ConfigurationPart {
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

    @Comment("Enable or disable debug mode.")
    public static boolean debug = false;

}

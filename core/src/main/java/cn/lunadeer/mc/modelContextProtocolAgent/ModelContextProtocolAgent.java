package cn.lunadeer.mc.modelContextProtocolAgent;

import cn.lunadeer.mc.modelContextProtocolAgent.api.McpAgentImpl;
import cn.lunadeer.mc.modelContextProtocolAgent.api.McpEventEmitterImpl;
import cn.lunadeer.mc.modelContextProtocolAgent.api.command.McpCommandManager;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.AgentWebSocketServer;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.Notification;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationManager;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.scheduler.Scheduler;
import cn.lunadeer.mc.modelContextProtocolAgent.provider.builtin.*;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.api.McpAgent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ModelContextProtocolAgent extends JavaPlugin {

    private AgentWebSocketServer webSocketServer;
    private McpAgentImpl mcpAgent;
    private CapabilityRegistry capabilityRegistry;
    private McpEventEmitterImpl eventEmitter;
    private McpCommandManager commandManager;

    public static class MainClassText extends ConfigurationPart {
        public String loadingConfig = "Loading configuration...";
        public String configLoadFailed = "Configuration load failed!";
        public String configLoaded = "Configuration loaded!";
        public String websocketStarting = "Starting WebSocket server...";
        public String websocketStarted = "WebSocket server started on {0}:{1}";
        public String websocketFailed = "Failed to start WebSocket server!";
        public String websocketStopping = "Stopping WebSocket server...";
        public String websocketStopped = "WebSocket server stopped";
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        new Notification(this);
        new XLogger(this);
        new Scheduler(this);

        // https://patorjk.com/software/taag/#p=display&f=Big&t=MCP-Agent&x=none&v=4&h=4&w=80&we=false
        XLogger.info("  __  __  _____ _____                               _   ");
        XLogger.info(" |  \\/  |/ ____|  __ \\\\        /\\\\                   | |  ");
        XLogger.info(" | \\\\  / | |    | |__) |_____ /  \\\\   __ _  ___ _ __ | |_ ");
        XLogger.info(" | |\\\\/| | |    |  ___/______/ / /\\\\ \\\\ / _` |/ _ \\\\ '_ \\\\| __|");
        XLogger.info(" | |  | | |____| |         / ____ \\\\ (_| |  __/ | | | |_ ");
        XLogger.info(" |_|  |_|\\\\_____|_|        /_/    \\\\_\\\\__, |\\\\___|_| |_|\\\\__|");
        XLogger.info("                                    __/ |               ");
        XLogger.info("                                   |___/                ");

        loadConfiguration();
        initializeProviderLayer();
        registerBuiltInProviders();
        startWebSocketServer();
        registerCommands();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        stopWebSocketServer();
    }

    private static ModelContextProtocolAgent instance;

    public static ModelContextProtocolAgent getInstance() {
        return instance;
    }

    /**
     * Load the configuration file and language files.
     */
    public void loadConfiguration() {
        try {
            XLogger.info(I18n.mainClassText.loadingConfig);
            ConfigurationManager.load(Configuration.class, new File(getDataFolder(), "config.yml"));
            XLogger.setDebug(Configuration.debug);
            XLogger.info(I18n.mainClassText.configLoaded);
            I18n.loadLanguageFiles(null, this, Configuration.language);
        } catch (Exception e) {
            XLogger.warn(I18n.mainClassText.configLoadFailed);
            XLogger.error(e);
        }
    }

    /**
     * Starts the WebSocket server.
     */
    public void startWebSocketServer() {
        try {
            XLogger.info(I18n.mainClassText.websocketStarting);
            webSocketServer = new AgentWebSocketServer(
                    Configuration.websocketServer.host,
                    Configuration.websocketServer.port
            );
            webSocketServer.start();
            XLogger.info(I18n.mainClassText.websocketStarted,
                    Configuration.websocketServer.host,
                    Configuration.websocketServer.port);
        } catch (Exception e) {
            XLogger.warn(I18n.mainClassText.websocketFailed);
            XLogger.error(e);
        }
    }

    /**
     * Stops the WebSocket server.
     */
    public void stopWebSocketServer() {
        if (webSocketServer != null) {
            XLogger.info(I18n.mainClassText.websocketStopping);
            webSocketServer.stop();
            XLogger.info(I18n.mainClassText.websocketStopped);
        }
    }

    /**
     * Gets the WebSocket server instance.
     *
     * @return the WebSocket server
     */
    public AgentWebSocketServer getWebSocketServer() {
        return webSocketServer;
    }

    /**
     * Initializes the provider layer.
     */
    private void initializeProviderLayer() {
        capabilityRegistry = new CapabilityRegistry();
        eventEmitter = new McpEventEmitterImpl();
        mcpAgent = new McpAgentImpl(capabilityRegistry, eventEmitter, getDescription().getVersion(), Configuration.agentInfo.agentId);

        // Register the McpAgent service with Bukkit's service manager
        getServer().getServicesManager().register(McpAgent.class, mcpAgent, this, org.bukkit.plugin.ServicePriority.Normal);

        XLogger.info("Provider layer initialized");
    }

    /**
     * Registers built-in providers.
     */
    private void registerBuiltInProviders() {
        try {
            capabilityRegistry.register(new WorldProvider(), this);
            capabilityRegistry.register(new PlayerProvider(), this);
            capabilityRegistry.register(new EntityProvider(), this);
            capabilityRegistry.register(new SystemProvider(), this);
            capabilityRegistry.register(new ChatProvider(), this);
            capabilityRegistry.register(new BlockProvider(), this);

            int totalCapabilities = capabilityRegistry.getCapabilities().size();
            XLogger.info("Registered " + totalCapabilities + " built-in capabilities");
        } catch (Exception e) {
            XLogger.warn("Failed to register built-in providers");
            XLogger.error(e);
        }
    }

    /**
     * Gets the MCP Agent implementation.
     *
     * @return the MCP Agent
     */
    public McpAgentImpl getMcpAgent() {
        return mcpAgent;
    }

    /**
     * Gets the capability registry.
     *
     * @return the capability registry
     */
    public CapabilityRegistry getCapabilityRegistry() {
        return capabilityRegistry;
    }

    /**
     * Gets the event emitter.
     *
     * @return the event emitter
     */
    public McpEventEmitterImpl getEventEmitter() {
        return eventEmitter;
    }

    /**
     * Registers admin commands.
     */
    private void registerCommands() {
        commandManager = new McpCommandManager(this);
        XLogger.info("Admin commands registered");
    }
}

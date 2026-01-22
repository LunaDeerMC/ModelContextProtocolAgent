package cn.lunadeer.mc.modelContextProtocolAgent.api.command;

import cn.lunadeer.mc.modelContextProtocolAgent.Configuration;
import cn.lunadeer.mc.modelContextProtocolAgent.ModelContextProtocolAgent;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.server.AgentWebSocketServer;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.session.SessionManager;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityManifest;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Command to display MCP Agent status.
 * <p>
 * Shows connection status, registered capabilities, and active sessions.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class McpStatusCommand extends McpCommand {

    private final ModelContextProtocolAgent plugin;

    public McpStatusCommand(ModelContextProtocolAgent plugin) {
        this.plugin = plugin;
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "mcp.admin.status")) {
            sendError(sender, "You don't have permission to use this command.");
            return false;
        }

        AgentWebSocketServer server = plugin.getWebSocketServer();
        CapabilityRegistry registry = plugin.getCapabilityRegistry();

        sendMessage(sender, "§6=== MCP Agent Status ===");
        sendMessage(sender, "§7Version: §f" + plugin.getDescription().getVersion());
        sendMessage(sender, "§7Agent ID: §f" + Configuration.agentInfo.agentId);

        // WebSocket Server Status
        if (server != null) {
            sendMessage(sender, "§7WebSocket Server: §aRunning");
            sendMessage(sender, "§7  Host: §f" + Configuration.websocketServer.host);
            sendMessage(sender, "§7  Port: §f" + Configuration.websocketServer.port);
        } else {
            sendMessage(sender, "§7WebSocket Server: §cStopped");
        }

        // Session Status
        SessionManager sessionManager = server != null ? server.getSessionManager() : null;
        if (sessionManager != null) {
            int totalSessions = sessionManager.getAllSessions().size();
            int authenticatedSessions = sessionManager.getAuthenticatedSessions().size();
            sendMessage(sender, "§7Active Sessions: §f" + authenticatedSessions + "§7/§f" + totalSessions);
        }

        // Capability Status
        List<CapabilityManifest> capabilities = registry.getCapabilities();
        sendMessage(sender, "§7Registered Capabilities: §f" + capabilities.size());

        // Show capability counts by type
        long contexts = capabilities.stream()
                .filter(c -> c.getType() == cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityType.CONTEXT)
                .count();
        long actions = capabilities.stream()
                .filter(c -> c.getType() == cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityType.ACTION)
                .count();
        long events = capabilities.stream()
                .filter(c -> c.getType() == cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityType.EVENT)
                .count();

        sendMessage(sender, "§7  Contexts: §f" + contexts);
        sendMessage(sender, "§7  Actions: §f" + actions);
        sendMessage(sender, "§7  Events: §f" + events);

        return true;
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        return List.of();
    }
}

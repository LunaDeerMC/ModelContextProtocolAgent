package cn.lunadeer.mc.mcp.api.command;

import cn.lunadeer.mc.mcp.MinecraftContextProtocolServer;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Command to control the MCP HTTP server.
 * <p>
 * Starts or stops the MCP HTTP server.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class McpHttpServerCommand extends McpCommand {

    private final MinecraftContextProtocolServer plugin;

    public McpHttpServerCommand(MinecraftContextProtocolServer plugin) {
        this.plugin = plugin;
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "mcp.admin.server")) {
            sendError(sender, "You don't have permission to use this command.");
            return false;
        }

        if (args.length < 1) {
            sendError(sender, "Usage: /mcp http <start|stop>");
            return false;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                return startServer(sender);
            case "stop":
                return stopServer(sender);
            default:
                sendError(sender, "Unknown subcommand: " + subCommand);
                sendError(sender, "Usage: /mcp http <start|stop>");
                return false;
        }
    }

    private boolean startServer(CommandSender sender) {
        if (plugin.getHttpMcpServer() != null) {
            sendError(sender, "MCP HTTP server is already running.");
            return false;
        }

        sendMessage(sender, "§6Starting MCP HTTP server...");
        try {
            plugin.startHttpMcpServer();
            sendSuccess(sender, "MCP HTTP server started successfully!");
            return true;
        } catch (Exception e) {
            sendError(sender, "Failed to start MCP HTTP server: " + e.getMessage());
            return false;
        }
    }

    private boolean stopServer(CommandSender sender) {
        if (plugin.getHttpMcpServer() == null) {
            sendError(sender, "MCP HTTP server is not running.");
            return false;
        }

        sendMessage(sender, "§6Stopping MCP HTTP server...");
        try {
            plugin.stopHttpMcpServer();
            sendSuccess(sender, "MCP HTTP server stopped successfully!");
            return true;
        } catch (Exception e) {
            sendError(sender, "Failed to stop MCP HTTP server: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop");
        }
        return List.of();
    }
}

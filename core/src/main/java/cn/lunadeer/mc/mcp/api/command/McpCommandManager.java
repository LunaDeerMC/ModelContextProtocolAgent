package cn.lunadeer.mc.mcp.api.command;

import cn.lunadeer.mc.mcp.MinecraftContextProtocolServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages MCP Server admin commands.
 * <p>
 * Registers and handles all MCP Server admin commands.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class McpCommandManager {

    private final MinecraftContextProtocolServer plugin;
    private final Map<String, McpCommand> commands = new HashMap<>();

    public McpCommandManager(MinecraftContextProtocolServer plugin) {
        this.plugin = plugin;
        registerCommands();
    }

    /**
     * Registers all MCP Server commands.
     */
    private void registerCommands() {
        // Register command handlers
        commands.put("status", new McpStatusCommand(plugin));
        commands.put("reload", new McpReloadCommand(plugin));
        commands.put("providers", new McpProvidersCommand(plugin));
        commands.put("capabilities", new McpCapabilitiesCommand(plugin));
        commands.put("sessions", new McpSessionsCommand(plugin));
        commands.put("kick", new McpKickCommand(plugin));
        commands.put("ws", new McpWebsocketServerCommand(plugin));
        commands.put("http", new McpHttpServerCommand(plugin));

        // Register commands with Bukkit
        for (Map.Entry<String, McpCommand> entry : commands.entrySet()) {
            PluginCommand command = plugin.getCommand("mcp " + entry.getKey());
            if (command != null) {
                command.setExecutor(entry.getValue());
                command.setTabCompleter(entry.getValue());
            }
        }

        // Register the main mcp command
        PluginCommand mcpCommand = plugin.getCommand("mcp");
        if (mcpCommand != null) {
            mcpCommand.setExecutor(this::onCommand);
            mcpCommand.setTabCompleter(this::onTabComplete);
        }
    }

    /**
     * Handles the main /mcp command.
     */
    private boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        McpCommand handler = commands.get(subCommand);

        if (handler != null) {
            // Pass remaining args to the handler
            String[] remainingArgs = new String[args.length - 1];
            System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
            return handler.execute(sender, remainingArgs);
        }

        sender.sendMessage("§cUnknown subcommand: " + subCommand);
        sendHelp(sender);
        return false;
    }

    /**
     * Handles tab completion for the main /mcp command.
     */
    private java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return java.util.List.copyOf(commands.keySet());
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return commands.keySet().stream()
                    .filter(cmd -> cmd.startsWith(partial))
                    .toList();
        }

        String subCommand = args[0].toLowerCase();
        McpCommand handler = commands.get(subCommand);

        if (handler != null) {
            String[] remainingArgs = new String[args.length - 1];
            System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
            return handler.getTabCompletions(sender, remainingArgs);
        }

        return java.util.List.of();
    }

    /**
     * Sends help message to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== MCP Server Commands ===");
        sender.sendMessage("§7/mcp status §f- Show Server status");
        sender.sendMessage("§7/mcp reload §f- Reload configuration");
        sender.sendMessage("§7/mcp providers §f- List registered providers");
        sender.sendMessage("§7/mcp capabilities §f- List registered capabilities");
        sender.sendMessage("§7/mcp sessions §f- List active sessions");
        sender.sendMessage("§7/mcp kick <id> §f- Kick a session");
        sender.sendMessage("§7/mcp ws <start|stop> §f- Control WebSocket server");
        sender.sendMessage("§7/mcp http <start|stop> §f- Control HTTP server");
    }
}

package cn.lunadeer.mc.modelContextProtocolAgent.provider.builtin;

import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpAction;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpProvider;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpBusinessException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Built-in MCP provider for chat-related capabilities.
 * <p>
 * Provides capabilities for sending messages and managing chat.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
@McpProvider(
    id = "mcp-agent-internal",
    name = "MCP Core Agent",
    version = "1.0.0",
    description = "Built-in capabilities for Minecraft chat management"
)
public class ChatProvider {

    /**
     * Sends a message to a player.
     *
     * @param playerName the player name
     * @param message the message to send
     * @param color the color code (optional)
     * @return true if successful
     */
    @McpAction(
        id = "chat.send.player",
        name = "Send Message to Player",
        description = "Sends a message to a specific player",
        risk = RiskLevel.LOW,
        permissions = {"mcp.action.chat.send.player"},
        tags = {"chat", "message", "send"}
    )
    public Boolean sendPlayerMessage(
        @Param(name = "playerName", required = true, description = "Player name")
        String playerName,
        @Param(name = "message", required = true, description = "Message to send")
        String message,
        @Param(name = "color", description = "Color code (e.g., 'red', 'green', 'gold')")
        String color
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Player not found or offline: " + playerName
            );
        }

        String formattedMessage = formatMessage(message, color);
        player.sendMessage(formattedMessage);
        return true;
    }

    /**
     * Broadcasts a message to all players.
     *
     * @param message the message to broadcast
     * @param color the color code (optional)
     * @return true if successful
     */
    @McpAction(
        id = "chat.broadcast",
        name = "Broadcast Message",
        description = "Broadcasts a message to all players",
        risk = RiskLevel.MEDIUM,
        permissions = {"mcp.action.chat.broadcast"},
        tags = {"chat", "message", "broadcast"}
    )
    public Boolean broadcastMessage(
        @Param(name = "message", required = true, description = "Message to broadcast")
        String message,
        @Param(name = "color", description = "Color code (e.g., 'red', 'green', 'gold')")
        String color
    ) {
        String formattedMessage = formatMessage(message, color);
        Bukkit.broadcastMessage(formattedMessage);
        return true;
    }

    /**
     * Sends an action bar message to a player.
     *
     * @param playerName the player name
     * @param message the message to send
     * @return true if successful
     */
    @McpAction(
        id = "chat.send.actionbar",
        name = "Send Action Bar Message",
        description = "Sends an action bar message to a player",
        risk = RiskLevel.LOW,
        permissions = {"mcp.action.chat.send.actionbar"},
        tags = {"chat", "message", "actionbar"}
    )
    public Boolean sendActionBarMessage(
        @Param(name = "playerName", required = true, description = "Player name")
        String playerName,
        @Param(name = "message", required = true, description = "Message to send")
        String message
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Player not found or offline: " + playerName
            );
        }

        // Note: Action bar requires Paper API or specific implementation
        // This is a placeholder implementation
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[Action Bar] " + message));
        return true;
    }

    /**
     * Formats a message with optional color.
     *
     * @param message the message
     * @param color the color code
     * @return the formatted message
     */
    private String formatMessage(String message, String color) {
        String formatted = message;

        if (color != null && !color.isEmpty()) {
            // Map color names to Minecraft color codes
            String colorCode = getColorCode(color);
            if (colorCode != null) {
                formatted = colorCode + message;
            }
        }

        // Translate color codes
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }

    /**
     * Gets the Minecraft color code for a color name.
     *
     * @param colorName the color name
     * @return the color code, or null if not found
     */
    private String getColorCode(String colorName) {
        switch (colorName.toLowerCase()) {
            case "black": return "&0";
            case "dark_blue": return "&1";
            case "dark_green": return "&2";
            case "dark_aqua": return "&3";
            case "dark_red": return "&4";
            case "dark_purple": return "&5";
            case "gold": return "&6";
            case "gray": return "&7";
            case "dark_gray": return "&8";
            case "blue": return "&9";
            case "green": return "&a";
            case "aqua": return "&b";
            case "red": return "&c";
            case "light_purple": return "&d";
            case "yellow": return "&e";
            case "white": return "&f";
            default: return null;
        }
    }
}

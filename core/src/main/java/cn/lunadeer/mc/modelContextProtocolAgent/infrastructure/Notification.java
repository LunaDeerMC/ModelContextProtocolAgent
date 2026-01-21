package cn.lunadeer.mc.modelContextProtocolAgent.infrastructure;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import static cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.Misc.formatString;
import static cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger.isDebug;

/**
 * Utility class for sending various types of notifications to players and all online users.
 * Supports chat messages, action bars, titles, subtitles, and boss bars.
 */
public class Notification {
    public static Notification instance;
    private static BukkitAudiences adventure = null;

    public Notification(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        this.prefix = "&6[&e" + plugin.getName() + "&6]&f";
        if (!Misc.isPaper()) {
            adventure = BukkitAudiences.create(plugin);
        }
    }

    private String prefix;
    private JavaPlugin plugin;

    /**
     * Sets the prefix for all messages sent by this Notification instance.
     *
     * @param prefix The prefix to set, which will be prepended to all messages.
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Sends an info message to a command sender.
     *
     * @param sender The command sender to send the message to.
     * @param msg    The message to send.
     */
    public static void info(CommandSender sender, String msg) {
        Component adventureMessage = LegacyToMiniMessage.parse(instance.prefix + " &2" + msg);
        if (adventure != null) {
            adventure.sender(sender).sendMessage(adventureMessage);
        } else {
            sender.sendMessage(adventureMessage);
        }
    }

    /**
     * Sends a formatted info message to a command sender.
     *
     * @param player The command sender to send the message to.
     * @param msg    The message template.
     * @param args   The arguments to format the message.
     */
    public static void info(CommandSender player, String msg, Object... args) {
        info(player, formatString(msg, args));
    }

    /**
     * Sends a warning message to a command sender.
     *
     * @param sender The command sender to send the message to.
     * @param msg    The message to send.
     */
    public static void warn(CommandSender sender, String msg) {
        Component adventureMessage = LegacyToMiniMessage.parse(instance.prefix + " &e" + msg);
        if (adventure != null) {
            adventure.sender(sender).sendMessage(adventureMessage);
        } else {
            sender.sendMessage(adventureMessage);
        }
    }

    /**
     * Sends a formatted warning message to a command sender.
     *
     * @param sender The command sender to send the message to.
     * @param msg    The message template.
     * @param args   The arguments to format the message.
     */
    public static void warn(CommandSender sender, String msg, Object... args) {
        warn(sender, formatString(msg, args));
    }

    /**
     * Sends an error message to a command sender.
     *
     * @param sender The command sender to send the message to.
     * @param msg    The message to send.
     */
    public static void error(CommandSender sender, String msg) {
        Component adventureMessage = LegacyToMiniMessage.parse(instance.prefix + " &c" + msg);
        if (adventure != null) {
            adventure.sender(sender).sendMessage(adventureMessage);
        } else {
            sender.sendMessage(adventureMessage);
        }
    }

    /**
     * Sends a formatted error message to a command sender.
     *
     * @param player The command sender to send the message to.
     * @param msg    The message template.
     * @param args   The arguments to format the message.
     */
    public static void error(CommandSender player, String msg, Object... args) {
        error(player, formatString(msg, args));
    }

    /**
     * Sends an error message to a command sender with an associated throwable.
     *
     * @param player The command sender to send the message to.
     * @param e      The throwable associated with the error.
     */
    public static void error(CommandSender player, Throwable e) {
        error(player, e.getMessage());
        if (isDebug()) {
            XLogger.error(e);
        }
    }

    /**
     * Broadcasts a message to all online players.
     *
     * @param msg The message to broadcast.
     */
    public static void all(String msg) {
        Component adventureMessage = LegacyToMiniMessage.parse(instance.prefix + " &2" + msg);
        if (adventure != null) {
            adventure.all().sendMessage(adventureMessage);
        } else {
            instance.plugin.getServer().broadcast(adventureMessage);
        }
    }

}

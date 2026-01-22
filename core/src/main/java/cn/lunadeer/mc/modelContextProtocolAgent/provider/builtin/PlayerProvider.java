package cn.lunadeer.mc.modelContextProtocolAgent.provider.builtin;

import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpAction;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpContext;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpProvider;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpBusinessException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Built-in MCP provider for player-related capabilities.
 * <p>
 * Provides capabilities for querying and managing player state including
 * player lists, player information, teleportation, and player actions.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
@McpProvider(
    id = "mcp-internal-player",
    name = "MCP Player Provider",
    version = "1.0.0",
    description = "Built-in capabilities for Minecraft player management"
)
public class PlayerProvider {

    /**
     * Gets a list of online players with pagination.
     *
     * @param pagination optional pagination parameters
     * @return the player list result
     */
    @McpContext(
        id = "player.list",
        name = "Get Player List",
        description = "Retrieves a list of online players with pagination",
        permissions = {"mcp.context.player.list"},
        tags = {"player", "list", "query"}
    )
    public PlayerListResult getPlayerList(
        @Param(name = "pagination", description = "Pagination parameters")
        PaginationParam pagination
    ) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        if (pagination == null) {
            pagination = new PaginationParam();
        }

        List<PlayerInfo> playerInfos = new ArrayList<>();
        for (Player player : players) {
            playerInfos.add(toPlayerInfo(player));
        }

        // Apply pagination
        int total = playerInfos.size();
        int pageSize = pagination.getPageSize();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int page = Math.min(pagination.getPage(), totalPages);
        int offset = pagination.getOffset();

        List<PlayerInfo> paginated = playerInfos.subList(
            Math.min(offset, total),
            Math.min(offset + pageSize, total)
        );

        PlayerListResult result = new PlayerListResult();
        result.setPlayers(paginated);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotalPages(totalPages);
        return result;
    }

    /**
     * Gets detailed information about a player.
     *
     * @param playerName optional player name
     * @param uuid optional player UUID
     * @return the player info
     */
    @McpContext(
        id = "player.info.get",
        name = "Get Player Info",
        description = "Retrieves detailed information about a player",
        permissions = {"mcp.context.player.info"},
        tags = {"player", "info", "query"}
    )
    public PlayerInfo getPlayerInfo(
        @Param(name = "playerName", description = "Player name")
        String playerName,
        @Param(name = "uuid", description = "Player UUID")
        String uuid
    ) {
        Player player = resolvePlayer(playerName, uuid);
        if (player == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Player not found"
            );
        }

        return toPlayerInfo(player);
    }

    /**
     * Teleports a player to a location.
     *
     * @param playerName the player name
     * @param location the target location
     * @param reason optional reason for teleport
     * @return the teleport result
     */
    @McpAction(
        id = "player.teleport",
        name = "Teleport Player",
        description = "Teleports a player to a specified location",
        risk = RiskLevel.MEDIUM,
        rollbackSupported = true,
        permissions = {"mcp.action.player.teleport"},
        tags = {"player", "teleport", "modify"}
    )
    public TeleportResult teleportPlayer(
        @Param(name = "playerName", required = true, description = "Player name")
        String playerName,
        @Param(name = "location", required = true, description = "Target location")
        LocationParam location,
        @Param(name = "reason", description = "Reason for teleport")
        String reason
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Player not found or offline: " + playerName
            );
        }

        Location previousLocation = player.getLocation().clone();
        Location targetLocation = toBukkitLocation(location);

        boolean success = player.teleport(targetLocation);

        TeleportResult result = new TeleportResult();
        result.setSuccess(success);
        result.setPreviousLocation(toLocationParam(previousLocation));
        result.setNewLocation(location);
        return result;
    }

    /**
     * Kicks a player from the server.
     *
     * @param playerName the player name
     * @param reason optional kick reason
     * @return the kick result
     */
    @McpAction(
        id = "player.kick",
        name = "Kick Player",
        description = "Kicks a player from the server",
        risk = RiskLevel.MEDIUM,
        permissions = {"mcp.action.player.kick"},
        tags = {"player", "kick", "modify"}
    )
    public KickResult kickPlayer(
        @Param(name = "playerName", required = true, description = "Player name")
        String playerName,
        @Param(name = "reason", description = "Kick reason")
        String reason
    ) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Player not found or offline: " + playerName
            );
        }

        String kickReason = reason != null ? reason : "Kicked by administrator";
        player.kick(net.kyori.adventure.text.Component.text(kickReason));

        KickResult result = new KickResult();
        result.setSuccess(true);
        result.setPlayerName(playerName);
        result.setReason(kickReason);
        return result;
    }

    /**
     * Resolves a player from name or UUID.
     *
     * @param playerName the player name
     * @param uuid the player UUID
     * @return the player, or null if not found
     */
    private Player resolvePlayer(String playerName, String uuid) {
        if (playerName != null) {
            return Bukkit.getPlayer(playerName);
        }
        if (uuid != null) {
            try {
                java.util.UUID playerUuid = java.util.UUID.fromString(uuid);
                return Bukkit.getPlayer(playerUuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Converts a Bukkit Player to PlayerInfo.
     *
     * @param player the Bukkit player
     * @return the player info
     */
    private PlayerInfo toPlayerInfo(Player player) {
        PlayerInfo info = new PlayerInfo();
        info.setName(player.getName());
        info.setUuid(player.getUniqueId().toString());
        info.setDisplayName(player.getDisplayName());
        info.setLocation(toLocationParam(player.getLocation()));
        info.setHealth(player.getHealth());
        info.setMaxHealth(player.getMaxHealth());
        info.setFoodLevel(player.getFoodLevel());
        info.setLevel(player.getLevel());
        info.setExp(player.getExp());
        info.setGameMode(player.getGameMode().name());
        info.setIsOp(player.isOp());
        info.setIsFlying(player.isFlying());
        info.setPing(player.getPing());
        info.setFirstPlayed(Instant.ofEpochMilli(player.getFirstPlayed()));
        info.setLastPlayed(Instant.ofEpochMilli(player.getLastPlayed()));
        return info;
    }

    /**
     * Converts a Bukkit Location to LocationParam.
     *
     * @param location the Bukkit location
     * @return the location param
     */
    private LocationParam toLocationParam(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationParam(
            location.getWorld() != null ? location.getWorld().getName() : null,
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    /**
     * Converts a LocationParam to Bukkit Location.
     *
     * @param locationParam the location param
     * @return the Bukkit location
     */
    private Location toBukkitLocation(LocationParam locationParam) {
        if (locationParam == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(locationParam.getWorld());
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + locationParam.getWorld()
            );
        }
        return new Location(
            world,
            locationParam.getX(),
            locationParam.getY(),
            locationParam.getZ(),
            locationParam.getYaw(),
            locationParam.getPitch()
        );
    }
}

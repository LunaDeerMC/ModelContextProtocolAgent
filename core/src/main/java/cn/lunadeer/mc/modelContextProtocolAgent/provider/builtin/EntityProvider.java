package cn.lunadeer.mc.modelContextProtocolAgent.provider.builtin;

import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpAction;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpContext;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpProvider;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpBusinessException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.LocationParam;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.PaginationParam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in MCP provider for entity-related capabilities.
 * <p>
 * Provides capabilities for querying and managing entities including
 * entity listing and entity removal operations.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
@McpProvider(
    id = "mcp-internal-entity",
    name = "MCP Entity Provider",
    version = "1.0.0",
    description = "Built-in capabilities for Minecraft entity management"
)
public class EntityProvider {

    /**
     * Lists entities in a world with optional filtering and pagination.
     *
     * @param worldName the name of the world
     * @param entityType optional entity type filter
     * @param location optional center location for radius filtering
     * @param radius optional radius from center location
     * @param pagination optional pagination parameters
     * @return the list of entities
     */
    @McpContext(
        id = "entity.list",
        name = "List Entities",
        description = "Lists entities in a world with optional filtering",
        permissions = {"mcp.context.entity.list"},
        tags = {"entity", "list", "query"}
    )
    public List<String> listEntities(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName,
        @Param(name = "entityType", description = "Entity type filter (e.g., 'Zombie', 'Cow')")
        String entityType,
        @Param(name = "location", description = "Center location for radius filtering")
        LocationParam location,
        @Param(name = "radius", description = "Radius from center location")
        Double radius,
        @Param(name = "pagination", description = "Pagination parameters")
        PaginationParam pagination
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        List<Entity> entities = world.getEntities();
        List<String> result = new ArrayList<>();

        Location center = null;
        if (location != null) {
            center = toBukkitLocation(location);
        }

        for (Entity entity : entities) {
            // Filter by entity type if specified
            if (entityType != null && !entity.getType().name().equalsIgnoreCase(entityType)) {
                continue;
            }

            // Filter by radius if specified
            if (center != null && radius != null) {
                double distance = entity.getLocation().distance(center);
                if (distance > radius) {
                    continue;
                }
            }

            result.add(entity.getType().name() + ":" + entity.getUniqueId());
        }

        // Apply pagination
        if (pagination != null) {
            int offset = pagination.getOffset();
            int limit = pagination.getPageSize();
            int from = Math.min(offset, result.size());
            int to = Math.min(offset + limit, result.size());
            result = result.subList(from, to);
        }

        return result;
    }

    /**
     * Removes entities from a world.
     *
     * @param worldName the name of the world
     * @param entityType optional entity type filter
     * @param location optional center location for radius filtering
     * @param radius optional radius from center location
     * @param excludePlayers whether to exclude players
     * @return the number of entities removed
     */
    @McpAction(
        id = "entity.remove",
        name = "Remove Entities",
        description = "Removes entities from a world",
        risk = RiskLevel.HIGH,
        snapshotRequired = true,
        rollbackSupported = true,
        permissions = {"mcp.action.entity.remove"},
        tags = {"entity", "remove", "modify"}
    )
    public Integer removeEntities(
        @Param(name = "worldName", required = true, description = "The name of the world")
        String worldName,
        @Param(name = "entityType", description = "Entity type filter (e.g., 'Zombie', 'Cow')")
        String entityType,
        @Param(name = "location", description = "Center location for radius filtering")
        LocationParam location,
        @Param(name = "radius", description = "Radius from center location")
        Double radius,
        @Param(name = "excludePlayers", description = "Whether to exclude players from removal")
        Boolean excludePlayers
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "World not found: " + worldName
            );
        }

        List<Entity> entities = world.getEntities();
        int removedCount = 0;

        Location center = null;
        if (location != null) {
            center = toBukkitLocation(location);
        }

        for (Entity entity : entities) {
            // Skip players if specified
            if (excludePlayers != null && excludePlayers && entity instanceof org.bukkit.entity.Player) {
                continue;
            }

            // Filter by entity type if specified
            if (entityType != null && !entity.getType().name().equalsIgnoreCase(entityType)) {
                continue;
            }

            // Filter by radius if specified
            if (center != null && radius != null) {
                double distance = entity.getLocation().distance(center);
                if (distance > radius) {
                    continue;
                }
            }

            entity.remove();
            removedCount++;
        }

        return removedCount;
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

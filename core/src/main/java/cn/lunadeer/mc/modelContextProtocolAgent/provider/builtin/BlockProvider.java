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
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.block.BlockInfo;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.block.BlockListResult;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.block.BlockLocationParam;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.dto.block.BlockSetting;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Built-in MCP provider for block-related capabilities.
 * <p>
 * Provides capabilities for querying and modifying blocks including
 * block information, setting blocks, and batch operations.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
@McpProvider(
        id = "mcp-internal-block",
        name = "MCP Block Provider",
        version = "1.0.0",
        description = "Built-in capabilities for Minecraft block management"
)
public class BlockProvider {

    /**
     * Gets information about a block at a specific location.
     *
     * @param location the location to query
     * @return the block information
     */
    @McpContext(
            id = "block.info.get",
            name = "Get Block Info",
            description = "Retrieves information about a block at a specific location",
            permissions = {"mcp.context.block.info"},
            tags = {"block", "info", "query"}
    )
    public BlockInfo getBlockInfo(
            @Param(name = "location", required = true, description = "The location to query")
            LocationParam location
    ) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + location.world()
            );
        }

        Block block = world.getBlockAt((int) location.x(), (int) location.y(), (int) location.z());
        Material material = block.getType();

        if (material == Material.AIR) {
            return new BlockInfo(
                    BlockLocationParam.create(location.world(), block.getX(), block.getY(), block.getZ()),
                    material.name(),
                    null,
                    null,
                    null
            );
        }

        BlockData blockData = block.getBlockData();
        String blockDataString = blockData.getAsString();

        // Get block state properties
        Map<String, String> properties = new HashMap<>();
        try {
            String[] parts = blockDataString.split("\\[");
            if (parts.length > 1) {
                String propsStr = parts[1].replace("]", "");
                String[] propPairs = propsStr.split(",");
                for (String pair : propPairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        properties.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        return new BlockInfo(
                BlockLocationParam.create(location.world(), block.getX(), block.getY(), block.getZ()),
                material.name(),
                blockDataString,
                properties,
                Integer.valueOf(block.getLightLevel())
        );
    }

    /**
     * Sets a block at a specific location.
     *
     * @param location  the location to set the block
     * @param material  the block material (e.g., "STONE", "DIRT")
     * @param blockData optional block data string (e.g., "minecraft:stone")
     * @param update    whether to update neighboring blocks
     * @return true if successful
     */
    @McpAction(
            id = "block.set",
            name = "Set Block",
            description = "Sets a block at a specific location",
            risk = RiskLevel.HIGH,
            snapshotRequired = true,
            rollbackSupported = true,
            permissions = {"mcp.action.block.set"},
            tags = {"block", "set", "modify"}
    )
    public Boolean setBlock(
            @Param(name = "location", required = true, description = "The location to set the block")
            BlockLocationParam location,
            @Param(name = "material", required = true, description = "The block material (e.g., 'STONE', 'DIRT')")
            String material,
            @Param(name = "blockData", description = "Optional block data string (e.g., 'minecraft:stone')")
            String blockData,
            @Param(name = "update", description = "Whether to update neighboring blocks", defaultValue = "true")
            Boolean update
    ) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + location.world()
            );
        }

        Block block = world.getBlockAt(location.x(), location.y(), location.z());

        try {
            Material blockMaterial = Material.getMaterial(material.toUpperCase());
            if (blockMaterial == null) {
                throw new McpBusinessException(
                        ErrorCode.OPERATION_FAILED.getErrorCode(),
                        "Invalid material: " + material
                );
            }

            if (blockData != null && !blockData.isEmpty()) {
                // Use block data string
                BlockData data = Bukkit.createBlockData(blockData);
                block.setBlockData(data, update != null ? update : true);
            } else {
                // Use material only
                block.setType(blockMaterial, update != null ? update : true);
            }

            return true;
        } catch (Exception e) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "Failed to set block: " + e.getMessage()
            );
        }
    }

    /**
     * Sets multiple blocks at specified locations.
     *
     * @param blocks the list of block settings
     * @param update whether to update neighboring blocks
     * @return the number of blocks set successfully
     */
    @McpAction(
            id = "block.set.batch",
            name = "Set Blocks (Batch)",
            description = "Sets multiple blocks at specified locations",
            risk = RiskLevel.HIGH,
            snapshotRequired = true,
            rollbackSupported = true,
            permissions = {"mcp.action.block.set.batch"},
            tags = {"block", "set", "batch", "modify"}
    )
    public Integer setBlocksBatch(
            @Param(name = "blocks", required = true, description = "List of block settings")
            List<BlockSetting> blocks,
            @Param(name = "update", description = "Whether to update neighboring blocks", defaultValue = "true")
            Boolean update
    ) {
        if (blocks == null || blocks.isEmpty()) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "No blocks specified"
            );
        }

        AtomicInteger successCount = new AtomicInteger();
        for (BlockSetting blockSetting : blocks) {
            try {
                World world = Bukkit.getWorld(blockSetting.blockLocation().world());
                if (world == null) {
                    continue;
                }
                world.getChunkAtAsyncUrgently(BlockLocationParam.toBukkitLocation(blockSetting.blockLocation()))
                        .thenAccept((chunk) -> {
                            Block block = world.getBlockAt(
                                    blockSetting.blockLocation().x(),
                                    blockSetting.blockLocation().y(),
                                    blockSetting.blockLocation().z()
                            );

                            Material blockMaterial = Material.getMaterial(blockSetting.material().toUpperCase());
                            if (blockMaterial == null) return;

                            if (blockSetting.blockData() != null && !blockSetting.blockData().isEmpty()) {
                                BlockData data = Bukkit.createBlockData(blockSetting.blockData());
                                block.setBlockData(data, update != null ? update : true);
                            } else {
                                block.setType(blockMaterial, update != null ? update : true);
                            }

                            successCount.getAndIncrement();
                        });
            } catch (Exception e) {
                // Continue with other blocks
            }
        }

        return successCount.get();
    }

    /**
     * Gets blocks in a specified area.
     *
     * @param worldName      the name of the world
     * @param minX           the minimum X coordinate
     * @param minY           the minimum Y coordinate
     * @param minZ           the minimum Z coordinate
     * @param maxX           the maximum X coordinate
     * @param maxY           the maximum Y coordinate
     * @param maxZ           the maximum Z coordinate
     * @param materialFilter optional material filter
     * @param pagination     optional pagination parameters
     * @return the list of block information
     */
    @McpContext(
            id = "block.list.area",
            name = "List Blocks in Area",
            description = "Gets blocks in a specified area",
            permissions = {"mcp.context.block.list.area"},
            tags = {"block", "list", "area", "query"}
    )
    public BlockListResult getBlocksInArea(
            @Param(name = "worldName", required = true, description = "The name of the world")
            String worldName,
            @Param(name = "minX", required = true, description = "Minimum X coordinate")
            Integer minX,
            @Param(name = "minY", required = true, description = "Minimum Y coordinate")
            Integer minY,
            @Param(name = "minZ", required = true, description = "Minimum Z coordinate")
            Integer minZ,
            @Param(name = "maxX", required = true, description = "Maximum X coordinate")
            Integer maxX,
            @Param(name = "maxY", required = true, description = "Maximum Y coordinate")
            Integer maxY,
            @Param(name = "maxZ", required = true, description = "Maximum Z coordinate")
            Integer maxZ,
            @Param(name = "materialFilter", description = "Material filter (e.g., 'STONE', 'DIRT')")
            String materialFilter,
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

        List<BlockInfo> blocks = new ArrayList<>();
        Material filterMaterial = null;
        if (materialFilter != null && !materialFilter.isEmpty()) {
            filterMaterial = Material.getMaterial(materialFilter.toUpperCase());
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();

                    if (material == Material.AIR) {
                        continue;
                    }

                    if (filterMaterial != null && material != filterMaterial) {
                        continue;
                    }

                    BlockData blockData = block.getBlockData();
                    String blockDataString = blockData.getAsString();

                    // Get block state properties
                    Map<String, String> properties = new HashMap<>();
                    try {
                        String[] parts = blockDataString.split("\\[");
                        if (parts.length > 1) {
                            String propsStr = parts[1].replace("]", "");
                            String[] propPairs = propsStr.split(",");
                            for (String pair : propPairs) {
                                String[] keyValue = pair.split("=");
                                if (keyValue.length == 2) {
                                    properties.put(keyValue[0].trim(), keyValue[1].trim());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }

                    BlockLocationParam locationParam = BlockLocationParam.create(worldName, x, y, z);
                    blocks.add(new BlockInfo(
                            locationParam,
                            material.name(),
                            blockDataString,
                            properties,
                            Integer.valueOf(block.getLightLevel())
                    ));
                }
            }
        }

        // Apply pagination
        if (pagination == null) {
            pagination = PaginationParam.createDefault();
        }

        int total = blocks.size();
        int pageSize = pagination.pageSize() != null ? pagination.pageSize() : 20;
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int page = Math.min(pagination.page() != null ? pagination.page() : 1, totalPages);
        int offset = pagination.getOffset();

        List<BlockInfo> paginated = blocks.subList(
                Math.min(offset, total),
                Math.min(offset + pageSize, total)
        );

        return new BlockListResult(paginated, total, page, pageSize, totalPages);
    }

    /**
     * Replaces blocks in a specified area with another block.
     *
     * @param worldName       the name of the world
     * @param minX            the minimum X coordinate
     * @param minY            the minimum Y coordinate
     * @param minZ            the minimum Z coordinate
     * @param maxX            the maximum X coordinate
     * @param maxY            the maximum Y coordinate
     * @param maxZ            the maximum Z coordinate
     * @param targetMaterial  the target material to replace with
     * @param sourceMaterial  optional source material to replace (if null, replaces all non-air blocks)
     * @param targetBlockData optional target block data string
     * @param update          whether to update neighboring blocks
     * @return the number of blocks replaced
     */
    @McpAction(
            id = "block.replace.area",
            name = "Replace Blocks in Area",
            description = "Replaces blocks in a specified area with another block",
            risk = RiskLevel.HIGH,
            snapshotRequired = true,
            rollbackSupported = true,
            permissions = {"mcp.action.block.replace.area"},
            tags = {"block", "replace", "area", "modify"}
    )
    public Integer replaceBlocksInArea(
            @Param(name = "worldName", required = true, description = "The name of the world")
            String worldName,
            @Param(name = "minX", required = true, description = "Minimum X coordinate")
            Integer minX,
            @Param(name = "minY", required = true, description = "Minimum Y coordinate")
            Integer minY,
            @Param(name = "minZ", required = true, description = "Minimum Z coordinate")
            Integer minZ,
            @Param(name = "maxX", required = true, description = "Maximum X coordinate")
            Integer maxX,
            @Param(name = "maxY", required = true, description = "Maximum Y coordinate")
            Integer maxY,
            @Param(name = "maxZ", required = true, description = "Maximum Z coordinate")
            Integer maxZ,
            @Param(name = "targetMaterial", required = true, description = "The target material to replace with")
            String targetMaterial,
            @Param(name = "sourceMaterial", description = "The source material to replace (if null, replaces all non-air blocks)")
            String sourceMaterial,
            @Param(name = "targetBlockData", description = "Optional target block data string")
            String targetBlockData,
            @Param(name = "update", description = "Whether to update neighboring blocks", defaultValue = "true")
            Boolean update
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + worldName
            );
        }

        Material targetMat = Material.getMaterial(targetMaterial.toUpperCase());
        if (targetMat == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "Invalid target material: " + targetMaterial
            );
        }

        Material sourceMat = null;
        if (sourceMaterial != null && !sourceMaterial.isEmpty()) {
            sourceMat = Material.getMaterial(sourceMaterial.toUpperCase());
            if (sourceMat == null) {
                throw new McpBusinessException(
                        ErrorCode.OPERATION_FAILED.getErrorCode(),
                        "Invalid source material: " + sourceMaterial
                );
            }
        }

        int replacedCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();

                    if (material == Material.AIR) {
                        continue;
                    }

                    if (sourceMat != null && material != sourceMat) {
                        continue;
                    }

                    try {
                        if (targetBlockData != null && !targetBlockData.isEmpty()) {
                            BlockData data = Bukkit.createBlockData(targetBlockData);
                            block.setBlockData(data, update != null ? update : true);
                        } else {
                            block.setType(targetMat, update != null ? update : true);
                        }
                        replacedCount++;
                    } catch (Exception e) {
                        // Continue with other blocks
                    }
                }
            }
        }

        return replacedCount;
    }

    /**
     * Clears blocks in a specified area (sets to air).
     *
     * @param worldName the name of the world
     * @param minX      the minimum X coordinate
     * @param minY      the minimum Y coordinate
     * @param minZ      the minimum Z coordinate
     * @param maxX      the maximum X coordinate
     * @param maxY      the maximum Y coordinate
     * @param maxZ      the maximum Z coordinate
     * @param update    whether to update neighboring blocks
     * @return the number of blocks cleared
     */
    @McpAction(
            id = "block.clear.area",
            name = "Clear Blocks in Area",
            description = "Clears blocks in a specified area (sets to air)",
            risk = RiskLevel.HIGH,
            snapshotRequired = true,
            rollbackSupported = true,
            permissions = {"mcp.action.block.clear.area"},
            tags = {"block", "clear", "area", "modify"}
    )
    public Integer clearBlocksInArea(
            @Param(name = "worldName", required = true, description = "The name of the world")
            String worldName,
            @Param(name = "minX", required = true, description = "Minimum X coordinate")
            Integer minX,
            @Param(name = "minY", required = true, description = "Minimum Y coordinate")
            Integer minY,
            @Param(name = "minZ", required = true, description = "Minimum Z coordinate")
            Integer minZ,
            @Param(name = "maxX", required = true, description = "Maximum X coordinate")
            Integer maxX,
            @Param(name = "maxY", required = true, description = "Maximum Y coordinate")
            Integer maxY,
            @Param(name = "maxZ", required = true, description = "Maximum Z coordinate")
            Integer maxZ,
            @Param(name = "update", description = "Whether to update neighboring blocks", defaultValue = "true")
            Boolean update
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + worldName
            );
        }

        int clearedCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, update != null ? update : true);
                        clearedCount++;
                    }
                }
            }
        }

        return clearedCount;
    }

    /**
     * Gets the material at a specific location.
     *
     * @param location the location to query
     * @return the material name
     */
    @McpContext(
            id = "block.material.get",
            name = "Get Block Material",
            description = "Retrieves the material of a block at a specific location",
            permissions = {"mcp.context.block.material"},
            tags = {"block", "material", "query"}
    )
    public String getBlockMaterial(
            @Param(name = "location", required = true, description = "The location to query")
            BlockLocationParam location
    ) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + location.world()
            );
        }

        Block block = world.getBlockAt(location.x(), location.y(), location.z());
        return block.getType().name();
    }

    /**
     * Gets the block data at a specific location.
     *
     * @param location the location to query
     * @return the block data string
     */
    @McpContext(
            id = "block.data.get",
            name = "Get Block Data",
            description = "Retrieves the block data of a block at a specific location",
            permissions = {"mcp.context.block.data"},
            tags = {"block", "data", "query"}
    )
    public String getBlockData(
            @Param(name = "location", required = true, description = "The location to query")
            BlockLocationParam location
    ) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + location.world()
            );
        }

        Block block = world.getBlockAt(location.x(), location.y(), location.z());
        BlockData blockData = block.getBlockData();
        return blockData.getAsString();
    }

    /**
     * Gets the light level at a specific location.
     *
     * @param location the location to query
     * @return the light level (0-15)
     */
    @McpContext(
            id = "block.light.get",
            name = "Get Block Light Level",
            description = "Retrieves the light level at a specific location",
            permissions = {"mcp.context.block.light"},
            tags = {"block", "light", "query"}
    )
    public Integer getBlockLight(
            @Param(name = "location", required = true, description = "The location to query")
            BlockLocationParam location
    ) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    "World not found: " + location.world()
            );
        }

        Block block = world.getBlockAt(location.x(), location.y(), location.z());
        return Integer.valueOf(block.getLightLevel());
    }

}

package cn.lunadeer.mc.modelContextProtocolAgent.provider.builtin;

import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpAction;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpProvider;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.Param;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpBusinessException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;

import java.util.List;

/**
 * Built-in MCP provider for system-related capabilities.
 * <p>
 * Provides capabilities for system operations including backup, restore,
 * and plugin management.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
@McpProvider(
    id = "mcp-internal-system",
    name = "MCP System Provider",
    version = "1.0.0",
    description = "Built-in capabilities for Minecraft system management"
)
public class SystemProvider {

    /**
     * Creates a backup of the specified worlds.
     *
     * @param worlds the list of world names to backup
     * @param description optional description of the backup
     * @return the backup result
     */
    @McpAction(
        id = "system.backup",
        name = "Create Backup",
        description = "Creates a backup of the specified worlds",
        risk = RiskLevel.HIGH,
        permissions = {"mcp.action.system.backup"},
        tags = {"system", "backup", "modify"}
    )
    public String createBackup(
        @Param(name = "worlds", description = "List of world names to backup")
        List<String> worlds,
        @Param(name = "description", description = "Description of the backup")
        String description
    ) {
        // Note: This is a placeholder implementation
        // In a real implementation, you would use a backup plugin or custom logic
        // to create world backups

        if (worlds == null || worlds.isEmpty()) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "No worlds specified for backup"
            );
        }

        // Placeholder response
        String backupId = "backup_" + System.currentTimeMillis();
        return "Backup created with ID: " + backupId + " for worlds: " + worlds;
    }

    /**
     * Restores a backup.
     *
     * @param backupId the backup ID to restore
     * @param confirm confirmation flag (must be true)
     * @return the restore result
     */
    @McpAction(
        id = "system.restore",
        name = "Restore Backup",
        description = "Restores a backup",
        risk = RiskLevel.CRITICAL,
        snapshotRequired = true,
        permissions = {"mcp.action.system.restore"},
        tags = {"system", "restore", "modify"}
    )
    public String restoreBackup(
        @Param(name = "backupId", required = true, description = "The backup ID to restore")
        String backupId,
        @Param(name = "confirm", required = true, description = "Confirmation flag (must be true)")
        Boolean confirm
    ) {
        if (confirm == null || !confirm) {
            throw new McpBusinessException(
                ErrorCode.OPERATION_FAILED.getErrorCode(),
                "Confirmation required for restore operation"
            );
        }

        // Note: This is a placeholder implementation
        // In a real implementation, you would use a backup plugin or custom logic
        // to restore world backups

        return "Backup " + backupId + " restored successfully";
    }

    /**
     * Reloads a plugin or all plugins.
     *
     * @param pluginName optional plugin name to reload
     * @return the reload result
     */
    @McpAction(
        id = "system.reload",
        name = "Reload Plugin",
        description = "Reloads a plugin or all plugins",
        risk = RiskLevel.HIGH,
        permissions = {"mcp.action.system.reload"},
        tags = {"system", "reload", "modify"}
    )
    public String reloadPlugin(
        @Param(name = "pluginName", description = "Plugin name to reload (empty for all plugins)")
        String pluginName
    ) {
        // Note: This is a placeholder implementation
        // In a real implementation, you would use Bukkit's reload mechanism
        // or specific plugin reload methods

        if (pluginName == null || pluginName.isEmpty()) {
            return "Reloading all plugins (placeholder - not implemented)";
        } else {
            return "Reloading plugin: " + pluginName + " (placeholder - not implemented)";
        }
    }
}

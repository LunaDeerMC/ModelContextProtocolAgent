package cn.lunadeer.mc.modelContextProtocolAgent.core.permission;

import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.CallerInfo;
import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.ExecutionContext;
import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.ExecutionInterceptor;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpSecurityException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * Permission Checker for MCP capabilities.
 * <p>
 * Validates that the caller has the required permissions and roles
 * to execute a capability. This interceptor runs early in the execution chain.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class PermissionChecker implements ExecutionInterceptor {

    /**
     * Text definitions for PermissionChecker.
     */
    public static class PermissionCheckerText extends ConfigurationPart {
        public String noCapabilitySpecified = "No capability specified";
        public String noCallerInformationAvailable = "No caller information available";
        public String permissionDeniedForCapability = "Permission denied for capability: {0}. Required: {1}, Caller has: {2}";
        public String insufficientPermissionsToExecuteCapability = "Insufficient permissions to execute capability: {0}";
        public String roleCheckFailedForCapability = "Role check failed for capability: {0}. Required role: {1}, Caller roles: {2}";
        public String insufficientRoleToExecuteCapability = "Insufficient role to execute capability: {0}. Required role: {1}";
        public String permissionCheckPassedForCapability = "Permission check passed for capability: {0}";
    }

    public static PermissionCheckerText permissionCheckerText = new PermissionCheckerText();

    private static final int ORDER = 100; // Early execution

    /**
     * Checks if the caller has permission to execute the capability.
     *
     * @param context the execution context
     * @return true to continue execution, false to skip
     * @throws McpSecurityException if permission check fails
     */
    @Override
    public boolean preHandle(ExecutionContext context) throws McpSecurityException {
        CapabilityDescriptor capability = context.getCapability();
        CallerInfo caller = context.getCaller();

        if (capability == null) {
            throw new McpSecurityException(
                    ErrorCode.PERMISSION_DENIED.getErrorCode(),
                    I18n.permissionCheckerText.noCapabilitySpecified
            );
        }

        if (caller == null) {
            throw new McpSecurityException(
                    ErrorCode.PERMISSION_DENIED.getErrorCode(),
                    I18n.permissionCheckerText.noCallerInformationAvailable
            );
        }

        // Check permissions
        Set<String> requiredPermissions = new HashSet<>(capability.getPermissions());
        if (!requiredPermissions.isEmpty()) {
            if (!caller.hasAllPermissions(requiredPermissions)) {
                XLogger.debug(I18n.permissionCheckerText.permissionDeniedForCapability
                        .replace("{0}", capability.getId())
                        .replace("{1}", requiredPermissions.toString())
                        .replace("{2}", caller.getPermissions().toString()));

                throw new McpSecurityException(
                        ErrorCode.PERMISSION_DENIED.getErrorCode(),
                        I18n.permissionCheckerText.insufficientPermissionsToExecuteCapability.replace("{0}", capability.getId())
                );
            }
        }

        // Check risk level requirements
        RiskLevel riskLevel = capability.getRiskLevel();
        if (riskLevel != null) {
            String requiredRole = getRequiredRoleForRiskLevel(riskLevel);
            if (requiredRole != null && !caller.hasRole(requiredRole)) {
                XLogger.debug(I18n.permissionCheckerText.roleCheckFailedForCapability
                        .replace("{0}", capability.getId())
                        .replace("{1}", requiredRole)
                        .replace("{2}", caller.getRoles().toString()));

                throw new McpSecurityException(
                        ErrorCode.PERMISSION_DENIED.getErrorCode(),
                        I18n.permissionCheckerText.insufficientRoleToExecuteCapability
                                .replace("{0}", capability.getId())
                                .replace("{1}", requiredRole)
                );
            }
        }

        XLogger.debug(I18n.permissionCheckerText.permissionCheckPassedForCapability, capability.getId());
        return true;
    }

    @Override
    public void postHandle(ExecutionContext context, Object result) {
        // No post-processing needed
    }

    @Override
    public void onError(ExecutionContext context, Throwable ex) {
        // No error handling needed
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Gets the required role for a given risk level.
     *
     * @param riskLevel the risk level
     * @return the required role, or null if no role required
     */
    private String getRequiredRoleForRiskLevel(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return null; // No role required for low risk
            case MEDIUM:
                return "operator";
            case HIGH:
                return "admin";
            case CRITICAL:
                return "super_admin";
            default:
                return null;
        }
    }
}

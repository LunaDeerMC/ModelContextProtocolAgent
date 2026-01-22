package cn.lunadeer.mc.modelContextProtocolAgent.core.registry;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpAction;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpContext;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpEvent;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.annotations.McpProvider;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.api.McpProviderRegistry;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityManifest;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.CapabilityType;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.RiskLevel;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.util.SchemaGenerator;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Capability Registry implementation.
 * <p>
 * Manages registration, lookup, and lifecycle of MCP capabilities and providers.
 * Uses concurrent data structures for thread-safe operations.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class CapabilityRegistry implements McpProviderRegistry {

    /**
     * Text definitions for CapabilityRegistry.
     */
    public static class CapabilityRegistryText extends ConfigurationPart {
        public String providerInstanceCannotBeNull = "Provider instance cannot be null";
        public String providerClassMustBeAnnotated = "Provider class {0} must be annotated with @McpProvider";
        public String providerWithIdIsAlreadyRegistered = "Provider with ID '{0}' is already registered";
        public String noCapabilitiesFoundInProvider = "No capabilities found in provider: {0}";
        public String registeredProvider = "Registered provider: {0} ({1} capabilities)";
        public String attemptedToUnregisterUnknownProviderInstance = "Attempted to unregister unknown provider instance";
        public String unregisteredProvidersForPlugin = "Unregistered {0} providers for plugin: {1}";
        public String unregisteredProvider = "Unregistered provider: {0}";
        public String unknownAnnotationType = "Unknown annotation type: {0}";
    }

    public static CapabilityRegistryText capabilityRegistryText = new CapabilityRegistryText();

    /**
     * Capability index: capabilityId -> CapabilityDescriptor.
     */
    private final Map<String, CapabilityDescriptor> capabilityIndex = new ConcurrentHashMap<>();

    /**
     * Provider index: providerId -> ProviderDescriptor.
     */
    private final Map<String, ProviderDescriptor> providerIndex = new ConcurrentHashMap<>();

    /**
     * Reverse mapping: provider instance -> providerId.
     */
    private final Map<Object, String> providerInstanceToId = new ConcurrentHashMap<>();

    /**
     * Constructs a new CapabilityRegistry.
     */
    public CapabilityRegistry() {
    }

    @Override
    public void register(Object providerInstance) {
        register(providerInstance, null);
    }

    @Override
    public void register(Object providerInstance, Plugin ownerPlugin) {
        if (providerInstance == null) {
            throw new IllegalArgumentException(I18n.capabilityRegistryText.providerInstanceCannotBeNull);
        }

        Class<?> providerClass = providerInstance.getClass();
        McpProvider providerAnnotation = providerClass.getAnnotation(McpProvider.class);

        if (providerAnnotation == null) {
            throw new IllegalArgumentException(
                    I18n.capabilityRegistryText.providerClassMustBeAnnotated.replace("{0}", providerClass.getName()));
        }

        String providerId = providerAnnotation.id();
        String providerName = providerAnnotation.name();
        String providerVersion = providerAnnotation.version();

        // Check if provider is already registered
        if (providerIndex.containsKey(providerId)) {
            throw new IllegalArgumentException(I18n.capabilityRegistryText.providerWithIdIsAlreadyRegistered.replace("{0}", providerId));
        }

        // Scan for capabilities
        List<CapabilityDescriptor> capabilities = scanCapabilities(providerInstance, providerId);

        if (capabilities.isEmpty()) {
            XLogger.warn(I18n.capabilityRegistryText.noCapabilitiesFoundInProvider, providerId);
        }

        // Create provider descriptor
        ProviderDescriptor providerDescriptor = new ProviderDescriptor(
                providerId,
                providerName,
                providerVersion,
                providerInstance,
                ownerPlugin,
                capabilities);

        // Register provider
        providerIndex.put(providerId, providerDescriptor);
        providerInstanceToId.put(providerInstance, providerId);

        // Register capabilities
        for (CapabilityDescriptor capability : capabilities) {
            capabilityIndex.put(capability.getId(), capability);
        }

        XLogger.info(I18n.capabilityRegistryText.registeredProvider, providerId, capabilities.size());
    }

    @Override
    public void unregister(Object providerInstance) {
        if (providerInstance == null) {
            return;
        }

        String providerId = providerInstanceToId.get(providerInstance);
        if (providerId == null) {
            XLogger.warn(I18n.capabilityRegistryText.attemptedToUnregisterUnknownProviderInstance);
            return;
        }

        unregisterByProviderId(providerId);
    }

    @Override
    public void unregisterAll(Plugin ownerPlugin) {
        if (ownerPlugin == null) {
            return;
        }

        // Find all providers owned by this plugin
        List<String> providerIds = providerIndex.values().stream()
                .filter(descriptor -> ownerPlugin.equals(descriptor.getOwnerPlugin()))
                .map(ProviderDescriptor::getId)
                .collect(Collectors.toList());

        // Unregister each provider
        for (String providerId : providerIds) {
            unregisterByProviderId(providerId);
        }

        if (!providerIds.isEmpty()) {
            XLogger.info(I18n.capabilityRegistryText.unregisteredProvidersForPlugin, providerIds.size(), ownerPlugin.getName());
        }
    }

    @Override
    public List<CapabilityManifest> getCapabilities() {
        return capabilityIndex.values().stream()
                .map(CapabilityDescriptor::getManifest)
                .collect(Collectors.toList());
    }

    @Override
    public List<CapabilityManifest> getCapabilities(String providerId) {
        ProviderDescriptor provider = providerIndex.get(providerId);
        if (provider == null) {
            return Collections.emptyList();
        }

        return provider.getCapabilities().stream()
                .map(CapabilityDescriptor::getManifest)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasCapability(String capabilityId) {
        return capabilityIndex.containsKey(capabilityId);
    }

    /**
     * Gets a capability descriptor by ID.
     *
     * @param capabilityId the capability ID
     * @return the capability descriptor, or null if not found
     */
    public CapabilityDescriptor getCapabilityDescriptor(String capabilityId) {
        return capabilityIndex.get(capabilityId);
    }

    /**
     * Gets all registered provider IDs.
     *
     * @return set of provider IDs
     */
    public Set<String> getProviderIds() {
        return providerIndex.keySet();
    }

    /**
     * Gets a provider descriptor by ID.
     *
     * @param providerId the provider ID
     * @return the provider descriptor, or null if not found
     */
    public ProviderDescriptor getProviderDescriptor(String providerId) {
        return providerIndex.get(providerId);
    }

    /**
     * Scans a provider instance for capability methods.
     *
     * @param providerInstance the provider instance
     * @param providerId the provider ID
     * @return list of capability descriptors
     */
    private List<CapabilityDescriptor> scanCapabilities(Object providerInstance, String providerId) {
        List<CapabilityDescriptor> capabilities = new ArrayList<>();
        Class<?> providerClass = providerInstance.getClass();

        for (Method method : providerClass.getDeclaredMethods()) {
            // Check for @McpContext
            McpContext contextAnnotation = method.getAnnotation(McpContext.class);
            if (contextAnnotation != null) {
                capabilities.add(createCapabilityDescriptor(
                        providerInstance, providerId, method, contextAnnotation, CapabilityType.CONTEXT));
            }

            // Check for @McpAction
            McpAction actionAnnotation = method.getAnnotation(McpAction.class);
            if (actionAnnotation != null) {
                capabilities.add(createCapabilityDescriptor(
                        providerInstance, providerId, method, actionAnnotation, CapabilityType.ACTION));
            }

            // Check for @McpEvent
            McpEvent eventAnnotation = method.getAnnotation(McpEvent.class);
            if (eventAnnotation != null) {
                capabilities.add(createCapabilityDescriptor(
                        providerInstance, providerId, method, eventAnnotation, CapabilityType.EVENT));
            }
        }

        return capabilities;
    }

    /**
     * Creates a capability descriptor from an annotated method.
     *
     * @param providerInstance the provider instance
     * @param providerId the provider ID
     * @param method the handler method
     * @param annotation the capability annotation
     * @param type the capability type
     * @return the capability descriptor
     */
    private CapabilityDescriptor createCapabilityDescriptor(
            Object providerInstance,
            String providerId,
            Method method,
            Object annotation,
            CapabilityType type) {

        String id;
        String name;
        String description;
        String version;
        List<String> permissions;
        List<String> tags;
        RiskLevel riskLevel = RiskLevel.LOW;
        boolean rollbackSupported = false;
        boolean snapshotRequired = false;
        boolean confirmRequired = false;
        boolean cacheable = true;
        int cacheTtl = 60;

        // Extract common fields from annotation
        if (annotation instanceof McpContext context) {
            id = context.id();
            name = context.name();
            description = context.description();
            version = context.version();
            permissions = Arrays.asList(context.permissions());
            tags = Arrays.asList(context.tags());
            cacheable = context.cacheable();
            cacheTtl = context.cacheTtl();
        } else if (annotation instanceof McpAction action) {
            id = action.id();
            name = action.name();
            description = action.description();
            version = action.version();
            permissions = Arrays.asList(action.permissions());
            tags = Arrays.asList(action.tags());
            riskLevel = action.risk();
            rollbackSupported = action.rollbackSupported();
            snapshotRequired = action.snapshotRequired();
            confirmRequired = action.confirmRequired();
            cacheable = false; // Actions are not cacheable
            cacheTtl = 0;
        } else if (annotation instanceof McpEvent event) {
            id = event.id();
            name = event.name();
            description = event.description();
            version = event.version();
            permissions = Arrays.asList(event.permissions());
            tags = Arrays.asList(event.tags());
            cacheable = false; // Events are not cacheable
            cacheTtl = 0;
        } else {
            throw new IllegalArgumentException(I18n.capabilityRegistryText.unknownAnnotationType.replace("{0}", annotation.getClass().getName()));
        }

        // Generate schemas
        java.util.Map<String, Object> parameterSchema = SchemaGenerator.generateParameterSchema(method);
        java.util.Map<String, Object> returnSchema = SchemaGenerator.generateReturnSchema(method);

        // Create manifest
        CapabilityManifest manifest = new CapabilityManifest();
        manifest.setId(id);
        manifest.setName(name);
        manifest.setDescription(description);
        manifest.setVersion(version);
        manifest.setType(type);
        manifest.setProviderId(providerId);
        manifest.setPermissions(permissions);
        manifest.setTags(tags);
        manifest.setRiskLevel(riskLevel);
        manifest.setRollbackSupported(rollbackSupported);
        manifest.setSnapshotRequired(snapshotRequired);
        manifest.setConfirmRequired(confirmRequired);
        manifest.setCacheable(cacheable);
        manifest.setCacheTtl(cacheTtl);
        manifest.setParameterSchema(parameterSchema);
        manifest.setReturnSchema(returnSchema);

        // Create descriptor
        return new CapabilityDescriptor(
                id,
                version,
                type,
                manifest,
                providerInstance,
                method,
                parameterSchema,
                returnSchema,
                riskLevel,
                permissions,
                rollbackSupported,
                snapshotRequired,
                confirmRequired,
                cacheable,
                cacheTtl,
                tags);
    }

    /**
     * Unregisters a provider by its ID.
     *
     * @param providerId the provider ID
     */
    private void unregisterByProviderId(String providerId) {
        ProviderDescriptor provider = providerIndex.remove(providerId);
        if (provider == null) {
            return;
        }

        // Remove all capabilities from this provider
        for (CapabilityDescriptor capability : provider.getCapabilities()) {
            capabilityIndex.remove(capability.getId());
        }

        // Remove provider instance mapping
        providerInstanceToId.remove(provider.getInstance());

        XLogger.info(I18n.capabilityRegistryText.unregisteredProvider, providerId);
    }
}

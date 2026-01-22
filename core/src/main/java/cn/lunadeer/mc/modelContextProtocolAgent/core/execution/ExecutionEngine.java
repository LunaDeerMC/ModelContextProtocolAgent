package cn.lunadeer.mc.modelContextProtocolAgent.core.execution;

import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.McpRequest;
import cn.lunadeer.mc.modelContextProtocolAgent.communication.message.McpResponse;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityDescriptor;
import cn.lunadeer.mc.modelContextProtocolAgent.core.registry.CapabilityRegistry;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpBusinessException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Execution Engine for MCP capabilities.
 * <p>
 * Processes capability invocation requests using a chain of interceptors
 * and executes the capability handler method.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class ExecutionEngine {

    /**
     * Text definitions for ExecutionEngine.
     */
    public static class ExecutionEngineText extends ConfigurationPart {
        public String capabilityNotFound = "Capability not found: {0}";
        public String unexpectedErrorDuringCapabilityExecution = "Unexpected error during capability execution";
        public String internalErrorDuringExecution = "Internal error during execution";
        public String capabilityExecuted = "Capability executed: {0}";
        public String failedToExecuteCapability = "Failed to execute capability: {0}";
        public String invalidEnumValue = "Invalid enum value '{0}' for type {1}. Valid values: {2}";
    }

    public static ExecutionEngineText executionEngineText = new ExecutionEngineText();

    private final CapabilityRegistry registry;
    private final List<ExecutionInterceptor> interceptors;

    /**
     * Constructs a new ExecutionEngine.
     *
     * @param registry the capability registry
     * @param interceptors the execution interceptors
     */
    public ExecutionEngine(CapabilityRegistry registry, List<ExecutionInterceptor> interceptors) {
        this.registry = registry;
        this.interceptors = interceptors != null ? interceptors : new ArrayList<>();
        // Sort interceptors by order
        this.interceptors.sort(Comparator.comparingInt(ExecutionInterceptor::getOrder));
    }

    /**
     * Executes a capability request.
     *
     * @param request the MCP request
     * @param caller the caller information
     * @return a future that completes with the response
     */
    public CompletableFuture<McpResponse> execute(McpRequest request, CallerInfo caller) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create execution context
                ExecutionContext context = createExecutionContext(request, caller);
                if (context == null) {
                    return McpResponse.error(
                            request.getId(),
                            ErrorCode.CAPABILITY_NOT_FOUND,
                            I18n.executionEngineText.capabilityNotFound.replace("{0}", request.getCapabilityId())
                    ).build();
                }

                // Build and execute the chain
                ExecutionChain chain = new ExecutionChain(interceptors, () -> invokeCapability(context));
                chain.proceed(context).join();

                // Return the response from context (set by interceptors or default)
                return context.getResponse() != null
                        ? context.getResponse()
                        : buildSuccessResponse(request.getId(), context.getResult());

            } catch (McpException ex) {
                // Try to find matching ErrorCode, fallback to OPERATION_FAILED
                ErrorCode errorCode = ErrorCode.OPERATION_FAILED;
                if (ex.getErrorCode() != null) {
                    try {
                        errorCode = ErrorCode.valueOf(ex.getErrorCode());
                    } catch (IllegalArgumentException e) {
                        // Use default
                    }
                }
                return McpResponse.error(request.getId(), errorCode, ex.getMessage()).build();
            } catch (Exception ex) {
                XLogger.error(I18n.executionEngineText.unexpectedErrorDuringCapabilityExecution, ex);
                return McpResponse.error(
                        request.getId(),
                        ErrorCode.INTERNAL_ERROR,
                        I18n.executionEngineText.internalErrorDuringExecution
                ).build();
            }
        });
    }

    /**
     * Creates an execution context from a request.
     *
     * @param request the MCP request
     * @param caller the caller information
     * @return the execution context, or null if capability not found
     */
    private ExecutionContext createExecutionContext(McpRequest request, CallerInfo caller) {
        String capabilityId = request.getCapabilityId();
        CapabilityDescriptor capability = registry.getCapabilityDescriptor(capabilityId);

        if (capability == null) {
            return null;
        }

        // Parse parameters
        Map<String, Object> parameters = request.getParameters();

        return new ExecutionContext(request, capability, caller, parameters);
    }

    /**
     * Invokes the capability handler method.
     *
     * @param context the execution context
     */
    private void invokeCapability(ExecutionContext context) {
        CapabilityDescriptor capability = context.getCapability();
        Method method = capability.getHandlerMethod();
        Object providerInstance = capability.getProviderInstance();

        try {
            // Prepare method arguments
            Object[] args = prepareMethodArguments(method, context.getParameters());

            // Invoke the method
            Object result = method.invoke(providerInstance, args);

            // Set the result in context
            context.setResult(result);

            XLogger.debug(I18n.executionEngineText.capabilityExecuted, capability.getId());

        } catch (Exception ex) {
            // Unwrap reflection exceptions
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

            if (cause instanceof McpException) {
                throw (McpException) cause;
            }

            throw new McpBusinessException(
                    ErrorCode.OPERATION_FAILED.getErrorCode(),
                    I18n.executionEngineText.failedToExecuteCapability.replace("{0}", capability.getId()),
                    cause
            );
        }
    }

    /**
     * Prepares method arguments from parameters map.
     *
     * @param method the handler method
     * @param parameters the parameters map
     * @return the prepared arguments array
     */
    private Object[] prepareMethodArguments(Method method, Map<String, Object> parameters) {
        java.lang.reflect.Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            java.lang.reflect.Parameter param = methodParams[i];
            String paramName = param.getName();
            Object paramValue = parameters.get(paramName);

            // Convert parameter value to the expected type
            args[i] = convertParameter(paramValue, param.getType());
        }

        return args;
    }

    /**
     * Converts a parameter value to the expected type.
     *
     * @param value the parameter value
     * @param targetType the target type
     * @return the converted value
     */
    private Object convertParameter(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // Handle primitive types
        if (targetType == int.class || targetType == Integer.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        }
        if (targetType == float.class || targetType == Float.class) {
            return value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }

        // Handle String
        if (targetType == String.class) {
            return value.toString();
        }

        // Handle enum types
        if (targetType.isEnum()) {
            return convertToEnum(value, targetType);
        }

        // Return as-is for complex types (will be handled by Jackson/Gson)
        return value;
    }

    /**
     * Converts a value to an enum constant.
     *
     * @param value the value to convert
     * @param enumType the enum type
     * @return the enum constant
     */
    @SuppressWarnings("unchecked")
    private Object convertToEnum(Object value, Class<?> enumType) {
        try {
            // Convert the value to string and look up the enum constant
            String enumName = value.toString().toUpperCase();
            return Enum.valueOf((Class<Enum>) enumType, enumName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    I18n.executionEngineText.invalidEnumValue
                            .replace("{0}", value.toString())
                            .replace("{1}", enumType.getSimpleName())
                            .replace("{2}", getEnumValues(enumType))
            );
        }
    }

    /**
     * Gets all valid enum values as a string.
     *
     * @param enumType the enum type
     * @return comma-separated list of enum values
     */
    private String getEnumValues(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < constants.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(constants[i].toString());
        }
        return sb.toString();
    }

    /**
     * Builds a success response.
     *
     * @param requestId the request ID
     * @param result the execution result
     * @return the success response
     */
    private McpResponse buildSuccessResponse(String requestId, Object result) {
        return McpResponse.success(requestId, result).build();
    }

    /**
     * Gets the list of interceptors.
     *
     * @return the interceptors list
     */
    public List<ExecutionInterceptor> getInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }
}

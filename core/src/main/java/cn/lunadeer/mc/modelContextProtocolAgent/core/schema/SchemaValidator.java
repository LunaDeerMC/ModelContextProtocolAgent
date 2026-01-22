package cn.lunadeer.mc.modelContextProtocolAgent.core.schema;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.exception.McpValidationException;
import cn.lunadeer.mc.modelContextProtocolAgentSDK.model.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * Schema Validator for MCP capability parameters and return values.
 * <p>
 * Validates parameters against JSON Schema definitions.
 * This is a simplified implementation that validates basic schema constraints.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class SchemaValidator {

    /**
     * Text definitions for SchemaValidator.
     */
    public static class SchemaValidatorText extends ConfigurationPart {
        public String requiredParameterMissing = "Required parameter '{0}' is missing for capability: {1}";
        public String parametersValidatedSuccessfully = "Parameters validated successfully for capability: {0}";
        public String errorDuringParameterValidation = "Error during parameter validation for capability: {0}";
        public String errorDuringParameterValidationDetail = "Error during parameter validation: {0}";
        public String returnValueValidatedSuccessfully = "Return value validated successfully for capability: {0}";
        public String errorDuringReturnValueValidation = "Error during return value validation for capability: {0}";
        public String errorDuringReturnValueValidationDetail = "Error during return value validation: {0}";
        public String parameterMustBeAtLeast = "Parameter '{0}' must be at least {1}";
        public String parameterMustBeAtMost = "Parameter '{0}' must be at most {1}";
        public String parameterDoesNotMatchPattern = "Parameter '{0}' does not match pattern: {1}";
        public String invalidRegexPattern = "Invalid regex pattern for parameter '{0}': {1}";
        public String mustBeAString = "{0} must be a string";
        public String mustBeAnInteger = "{0} must be an integer";
        public String mustBeANumber = "{0} must be a number";
        public String mustBeABoolean = "{0} must be a boolean";
        public String mustBeAnArray = "{0} must be an array";
        public String mustBeAnObject = "{0} must be an object";
        public String mustBeNull = "{0} must be null";
        public String unknownType = "Unknown type '{0}' for {1}, skipping type validation";
    }

    public static SchemaValidatorText schemaValidatorText = new SchemaValidatorText();

    /**
     * Validates request parameters against the capability's parameter schema.
     *
     * @param capabilityId the capability ID
     * @param parameterSchema the parameter schema (JSON Schema format)
     * @param parameters the parameters to validate
     * @throws McpValidationException if validation fails
     */
    public void validateParameters(String capabilityId,
                                   Map<String, Object> parameterSchema,
                                   Map<String, Object> parameters) throws McpValidationException {
        if (parameterSchema == null || parameterSchema.isEmpty()) {
            return; // No schema to validate against
        }

        try {
            // Validate required parameters
            List<?> requiredList = (List<?>) parameterSchema.get("required");
            if (requiredList != null) {
                for (Object requiredParam : requiredList) {
                    String paramName = requiredParam.toString();
                    if (!parameters.containsKey(paramName)) {
                        throw new McpValidationException(
                                ErrorCode.PARAMETER_REQUIRED.getErrorCode(),
                                I18n.schemaValidatorText.requiredParameterMissing.replace("{0}", paramName).replace("{1}", capabilityId)
                        );
                    }
                }
            }

            // Validate parameter types and constraints
            Map<?, ?> properties = (Map<?, ?>) parameterSchema.get("properties");
            if (properties != null) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String paramName = entry.getKey().toString();
                    Map<?, ?> paramSchema = (Map<?, ?>) entry.getValue();

                    if (parameters.containsKey(paramName)) {
                        validateParameterType(paramName, parameters.get(paramName), paramSchema);
                    }
                }
            }

            XLogger.debug(I18n.schemaValidatorText.parametersValidatedSuccessfully, capabilityId);

        } catch (McpValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            XLogger.error(I18n.schemaValidatorText.errorDuringParameterValidation, capabilityId, ex);
            throw new McpValidationException(
                    ErrorCode.SCHEMA_VALIDATION_FAILED.getErrorCode(),
                    I18n.schemaValidatorText.errorDuringParameterValidationDetail.replace("{0}", ex.getMessage())
            );
        }
    }

    /**
     * Validates return value against the capability's return schema.
     *
     * @param capabilityId the capability ID
     * @param returnSchema the return schema (JSON Schema format)
     * @param returnValue the return value to validate
     * @throws McpValidationException if validation fails
     */
    public void validateReturn(String capabilityId,
                               Map<String, Object> returnSchema,
                               Object returnValue) throws McpValidationException {
        if (returnSchema == null || returnSchema.isEmpty()) {
            return; // No schema to validate against
        }

        try {
            // Validate return type
            String expectedType = (String) returnSchema.get("type");
            if (expectedType != null && returnValue != null) {
                validateType("return value", returnValue, expectedType);
            }

            XLogger.debug(I18n.schemaValidatorText.returnValueValidatedSuccessfully, capabilityId);

        } catch (McpValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            XLogger.error(I18n.schemaValidatorText.errorDuringReturnValueValidation, capabilityId, ex);
            throw new McpValidationException(
                    ErrorCode.SCHEMA_VALIDATION_FAILED.getErrorCode(),
                    I18n.schemaValidatorText.errorDuringReturnValueValidationDetail.replace("{0}", ex.getMessage())
            );
        }
    }

    /**
     * Validates a single parameter's type and constraints.
     *
     * @param paramName the parameter name
     * @param paramValue the parameter value
     * @param paramSchema the parameter schema
     */
    private void validateParameterType(String paramName, Object paramValue, Map<?, ?> paramSchema) {
        String type = (String) paramSchema.get("type");
        if (type != null) {
            validateType(paramName, paramValue, type);
        }

        // Validate minimum/maximum for numbers
        if (paramValue instanceof Number) {
            Number number = (Number) paramValue;
            Double minimum = getDouble(paramSchema.get("minimum"));
            Double maximum = getDouble(paramSchema.get("maximum"));

            if (minimum != null && number.doubleValue() < minimum) {
                throw new McpValidationException(
                        ErrorCode.PARAMETER_INVALID.getErrorCode(),
                        I18n.schemaValidatorText.parameterMustBeAtLeast.replace("{0}", paramName).replace("{1}", minimum.toString())
                );
            }

            if (maximum != null && number.doubleValue() > maximum) {
                throw new McpValidationException(
                        ErrorCode.PARAMETER_INVALID.getErrorCode(),
                        I18n.schemaValidatorText.parameterMustBeAtMost.replace("{0}", paramName).replace("{1}", maximum.toString())
                );
            }
        }

        // Validate pattern for strings
        if (paramValue instanceof String) {
            String pattern = (String) paramSchema.get("pattern");
            if (pattern != null && !pattern.isEmpty()) {
                // Simple pattern validation (basic regex check)
                try {
                    if (!((String) paramValue).matches(pattern)) {
                        throw new McpValidationException(
                                ErrorCode.PARAMETER_INVALID.getErrorCode(),
                                I18n.schemaValidatorText.parameterDoesNotMatchPattern.replace("{0}", paramName).replace("{1}", pattern)
                        );
                    }
                } catch (Exception ex) {
                    XLogger.warn(I18n.schemaValidatorText.invalidRegexPattern.replace("{0}", paramName).replace("{1}", pattern));
                }
            }
        }
    }

    /**
     * Validates that a value matches the expected type.
     *
     * @param name the value name (for error messages)
     * @param value the value to validate
     * @param expectedType the expected JSON type
     */
    private void validateType(String name, Object value, String expectedType) {
        switch (expectedType) {
            case "string":
                if (!(value instanceof String)) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeAString.replace("{0}", name)
                    );
                }
                break;
            case "integer":
                if (!(value instanceof Number)) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeAnInteger.replace("{0}", name)
                    );
                }
                break;
            case "number":
                if (!(value instanceof Number)) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeANumber.replace("{0}", name)
                    );
                }
                break;
            case "boolean":
                if (!(value instanceof Boolean)) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeABoolean.replace("{0}", name)
                    );
                }
                break;
            case "array":
                if (!(value instanceof List)) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeAnArray.replace("{0}", name)
                    );
                }
                break;
            case "object":
                if (!(value instanceof Map)) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeAnObject.replace("{0}", name)
                    );
                }
                break;
            case "null":
                if (value != null) {
                    throw new McpValidationException(
                            ErrorCode.PARAMETER_INVALID.getErrorCode(),
                            I18n.schemaValidatorText.mustBeNull.replace("{0}", name)
                    );
                }
                break;
            default:
                // Unknown type, skip validation
                XLogger.debug(I18n.schemaValidatorText.unknownType.replace("{0}", expectedType).replace("{1}", name));
        }
    }

    /**
     * Gets a double value from an object.
     *
     * @param obj the object
     * @return the double value, or null if not a valid number
     */
    private Double getDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}


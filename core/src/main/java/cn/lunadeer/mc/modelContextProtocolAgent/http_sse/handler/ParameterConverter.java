package cn.lunadeer.mc.modelContextProtocolAgent.http_sse.handler;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles parameter conversion for capability invocation.
 * <p>
 * Converts JSON parameters to Java types with support for:
 * - Primitive types (int, long, double, float, boolean)
 * - Wrapper types (Integer, Long, Double, Float, Boolean)
 * - String
 * - Enums
 * - JsonObject and JsonElement
 * - Generic types (List&lt;T&gt;, Map&lt;K, V&gt;)
 * - Complex types (POJOs, Records) via Gson
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class ParameterConverter {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Converts a parameter to the expected type.
     */
    public Object convertParameter(Object value, java.lang.reflect.Parameter parameter) {
        if (value == null) {
            return null;
        }

        Class<?> targetType = parameter.getType();
        Type genericType = parameter.getParameterizedType();

        // Handle enum conversion
        if (targetType.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<Enum> enumType = (Class<Enum>) targetType;
            if (value instanceof String) {
                // Convert string to enum
                return Enum.valueOf(enumType, (String) value);
            } else if (value instanceof Enum) {
                return value;
            } else {
                // Try to convert to string first
                return Enum.valueOf(enumType, value.toString());
            }
        }

        // Handle common conversions
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetType == Float.class || targetType == float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.parseFloat(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        } else if (targetType == JsonObject.class) {
            if (value instanceof JsonObject) {
                return value;
            }
            return gson.toJsonTree(value).getAsJsonObject();
        } else if (targetType == JsonElement.class) {
            return gson.toJsonTree(value);
        } else {
            // Try to convert using Gson with proper type handling
            return convertWithGson(value, targetType, genericType);
        }
    }

    /**
     * Converts a value using Gson with proper type handling for generic types.
     */
    private Object convertWithGson(Object value, Class<?> targetType, Type genericType) {
        // Handle List types with generic parameters
        if (List.class.isAssignableFrom(targetType)) {
            if (value instanceof List) {
                // For List<T>, we need to use TypeToken to preserve generic type info
                // If we have the generic type, use it for proper deserialization
                if (genericType instanceof ParameterizedType) {
                    try {
                        return gson.fromJson(gson.toJson(value), genericType);
                    } catch (Exception e) {
                        XLogger.error("Failed to convert List with generic type: " + e.getMessage(), e);
                    }
                }
                // Fallback: convert each element individually
                List<?> sourceList = (List<?>) value;
                List<Object> result = new ArrayList<>();
                for (Object item : sourceList) {
                    if (item instanceof Map) {
                        // Try to convert Map to the target element type
                        // This is a best-effort conversion
                        result.add(gson.fromJson(gson.toJson(item), Object.class));
                    } else {
                        result.add(item);
                    }
                }
                return result;
            }
        }

        // Handle Map types with generic parameters
        if (Map.class.isAssignableFrom(targetType)) {
            if (value instanceof Map) {
                // For Map<K, V>, we need to use TypeToken to preserve generic type info
                // If we have the generic type, use it for proper deserialization
                if (genericType instanceof ParameterizedType) {
                    try {
                        return gson.fromJson(gson.toJson(value), genericType);
                    } catch (Exception e) {
                        XLogger.error("Failed to convert Map with generic type: " + e.getMessage(), e);
                    }
                }
                // Fallback: convert each entry individually
                Map<?, ?> sourceMap = (Map<?, ?>) value;
                Map<Object, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                    Object key = entry.getKey();
                    Object val = entry.getValue();

                    // Convert key if needed
                    Object convertedKey = key;
                    if (key instanceof String) {
                        // Try to convert to appropriate type
                        convertedKey = key;
                    }

                    // Convert value if needed
                    Object convertedValue = val;
                    if (val instanceof Map) {
                        convertedValue = gson.fromJson(gson.toJson(val), Object.class);
                    }

                    result.put(convertedKey, convertedValue);
                }
                return result;
            }
        }

        // For other complex types (records, POJOs), use Gson's default behavior
        // Gson can handle Java Records with proper configuration
        try {
            return gson.fromJson(gson.toJson(value), targetType);
        } catch (Exception e) {
            XLogger.error("Failed to convert parameter to type " + targetType.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets a default value for a parameter type.
     */
    public Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return 0;
        } else if (type == long.class || type == Long.class) {
            return 0L;
        } else if (type == double.class || type == Double.class) {
            return 0.0;
        } else if (type == float.class || type == Float.class) {
            return 0.0f;
        } else if (type == boolean.class || type == Boolean.class) {
            return false;
        } else {
            return null;
        }
    }

    /**
     * Parses a default value string to appropriate type.
     *
     * @param defaultValue the default value string
     * @return parsed value
     */
    public Object parseDefaultValue(String defaultValue) {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return null;
        }

        if ("true".equalsIgnoreCase(defaultValue) || "false".equalsIgnoreCase(defaultValue)) {
            return Boolean.parseBoolean(defaultValue);
        }

        try {
            if (defaultValue.contains(".")) {
                return Double.parseDouble(defaultValue);
            } else {
                return Integer.parseInt(defaultValue);
            }
        } catch (NumberFormatException e) {
            // Return as string
            return defaultValue;
        }
    }
}

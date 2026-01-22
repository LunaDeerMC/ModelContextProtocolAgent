package cn.lunadeer.mc.modelContextProtocolAgent.core.audit;

import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.CallerInfo;
import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.ExecutionContext;
import cn.lunadeer.mc.modelContextProtocolAgent.core.execution.ExecutionInterceptor;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Audit Logger for MCP capability execution.
 * <p>
 * Records all capability executions for auditing purposes.
 * Uses a background thread to write audit events asynchronously.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class AuditLogger implements ExecutionInterceptor {

    /**
     * Text definitions for AuditLogger.
     */
    public static class AuditLoggerText extends ConfigurationPart {
        public String redacted = "***REDACTED***";
        public String mapWithEntries = "Map with {0} entries";
        public String listWithItems = "List with {0} items";
        public String auditLogFormat = "[AUDIT] {0} | {1} | {2} | {3} | {4} | {5}";
        public String success = "SUCCESS";
        public String failed = "FAILED";
        public String emptyString = "";
        public String errorWritingAuditEvent = "Error writing audit event";
    }

    public static AuditLoggerText auditLoggerText = new AuditLoggerText();

    private static final int ORDER = 1000; // Late execution
    private final BlockingQueue<AuditEvent> eventQueue = new LinkedBlockingQueue<>();
    private final Thread writerThread;
    private volatile boolean running = true;

    /**
     * Constructs a new AuditLogger.
     */
    public AuditLogger() {
        this.writerThread = new Thread(this::writeEvents, "MCP-AuditLogger-Writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    @Override
    public boolean preHandle(ExecutionContext context) {
        // Record the start of execution
        AuditEvent event = buildEvent(context, AuditEventType.INVOKE, null, null);
        log(event);
        return true; // Always continue
    }

    @Override
    public void postHandle(ExecutionContext context, Object result) {
        // Record successful completion
        AuditEvent event = buildEvent(context, AuditEventType.COMPLETED, result, null);
        log(event);
    }

    @Override
    public void onError(ExecutionContext context, Throwable ex) {
        // Record failure
        AuditEvent event = buildEvent(context, AuditEventType.FAILED, null, ex.getMessage());
        log(event);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Logs an audit event.
     *
     * @param event the audit event
     */
    public void log(AuditEvent event) {
        if (event != null) {
            eventQueue.offer(event);
        }
    }

    /**
     * Builds an audit event from the execution context.
     *
     * @param context the execution context
     * @param eventType the event type
     * @param result the execution result
     * @param error the error message
     * @return the audit event
     */
    private AuditEvent buildEvent(ExecutionContext context, AuditEventType eventType,
                                 Object result, String error) {
        CallerInfo caller = context.getCaller();
        Map<String, Object> sanitizedRequest = sanitizeRequest(context.getParameters());

        return new AuditEvent.Builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .eventType(eventType)
                .capabilityId(context.getCapabilityId())
                .caller(caller)
                .request(sanitizedRequest)
                .response(sanitizeResponse(result))
                .riskLevel(context.getRiskLevel())
                .metadata(buildMetadata(context))
                .success(error == null)
                .error(error)
                .build();
    }

    /**
     * Sanitizes request parameters to remove sensitive information.
     *
     * @param parameters the original parameters
     * @return sanitized parameters
     */
    private Map<String, Object> sanitizeRequest(Map<String, Object> parameters) {
        if (parameters == null) {
            return new HashMap<>();
        }

        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Sanitize sensitive fields
            if (isSensitiveField(key)) {
                sanitized.put(key, I18n.auditLoggerText.redacted);
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    /**
     * Sanitizes response data.
     *
     * @param response the original response
     * @return sanitized response
     */
    private Object sanitizeResponse(Object response) {
        if (response == null) {
            return null;
        }

        // For complex objects, return a summary
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            if (map.size() > 10) {
                return I18n.auditLoggerText.mapWithEntries.replace("{0}", String.valueOf(map.size()));
            }
        } else if (response instanceof List) {
            List<?> list = (List<?>) response;
            if (list.size() > 10) {
                return I18n.auditLoggerText.listWithItems.replace("{0}", String.valueOf(list.size()));
            }
        }

        return response;
    }

    /**
     * Checks if a field name indicates sensitive data.
     *
     * @param fieldName the field name
     * @return true if sensitive
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return lower.contains("password") ||
               lower.contains("token") ||
               lower.contains("secret") ||
               lower.contains("key") ||
               lower.contains("auth") ||
               lower.contains("credential");
    }

    /**
     * Builds metadata for the audit event.
     *
     * @param context the execution context
     * @return the metadata map
     */
    private Map<String, Object> buildMetadata(ExecutionContext context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("requestId", context.getRequestId());
        metadata.put("capabilityType", context.getCapabilityType());
        metadata.put("timestamp", Instant.now().toString());
        return metadata;
    }

    /**
     * Background thread that writes audit events.
     */
    private void writeEvents() {
        while (running || !eventQueue.isEmpty()) {
            try {
                AuditEvent event = eventQueue.take();
                writeEventToFile(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                XLogger.error(I18n.auditLoggerText.errorWritingAuditEvent, ex);
            }
        }
    }

    /**
     * Writes a single audit event to storage.
     *
     * @param event the audit event
     */
    private void writeEventToFile(AuditEvent event) {
        // TODO: Implement file-based audit logging
        // For now, just log to console
        String logEntry = String.format(
                I18n.auditLoggerText.auditLogFormat,
                event.timestamp(),
                event.eventType(),
                event.capabilityId(),
                event.caller() != null ? event.caller().getId() : "unknown",
                event.success() ? I18n.auditLoggerText.success : I18n.auditLoggerText.failed,
                event.error() != null ? event.error() : I18n.auditLoggerText.emptyString
        );

        if (event.success()) {
            XLogger.info(logEntry);
        } else {
            XLogger.warn(logEntry);
        }
    }

    /**
     * Stops the audit logger.
     */
    public void stop() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }

    /**
     * Gets the number of pending audit events.
     *
     * @return the queue size
     */
    public int getPendingEventCount() {
        return eventQueue.size();
    }
}

package cn.lunadeer.mc.modelContextProtocolAgent.core.execution;

import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.I18n;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.XLogger;
import cn.lunadeer.mc.modelContextProtocolAgent.infrastructure.configuration.ConfigurationPart;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Execution chain for capability invocation.
 * <p>
 * Manages the execution of interceptors in order and the final capability invocation.
 * </p>
 *
 * @author ZhangYuheng
 * @since 1.0.0
 */
public class ExecutionChain {

    /**
     * Text definitions for ExecutionChain.
     */
    public static class ExecutionChainText extends ConfigurationPart {
        public String executionSkippedByInterceptor = "Execution skipped by interceptor: {0}";
        public String errorInInterceptorOnError = "Error in interceptor onError: {0}";
    }

    public static ExecutionChainText executionChainText = new ExecutionChainText();

    private final List<ExecutionInterceptor> interceptors;
    private final Runnable target;
    private int currentIndex = 0;

    /**
     * Constructs a new ExecutionChain.
     *
     * @param interceptors the list of interceptors
     * @param target the target execution (capability invocation)
     */
    public ExecutionChain(List<ExecutionInterceptor> interceptors, Runnable target) {
        this.interceptors = interceptors;
        this.target = target;
    }

    /**
     * Proceeds with the execution chain.
     *
     * @param context the execution context
     * @return a future that completes when the chain is done
     */
    public CompletableFuture<Void> proceed(ExecutionContext context) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Execute preHandle for all interceptors
                for (int i = currentIndex; i < interceptors.size(); i++) {
                    currentIndex = i;
                    ExecutionInterceptor interceptor = interceptors.get(i);

                    boolean shouldContinue = interceptor.preHandle(context);
                    if (!shouldContinue) {
                        // Interceptor requested to skip execution
                        context.setSkipped(true);
                        XLogger.debug(I18n.executionChainText.executionSkippedByInterceptor, interceptor.getClass().getSimpleName());
                        return;
                    }
                }

                // Execute target if not skipped
                if (!context.isSkipped()) {
                    target.run();
                }

                // Execute postHandle for all interceptors (in reverse order)
                for (int i = interceptors.size() - 1; i >= 0; i--) {
                    ExecutionInterceptor interceptor = interceptors.get(i);
                    interceptor.postHandle(context, context.getResult());
                }
            } catch (Throwable ex) {
                // Execute onError for all interceptors
                for (int i = currentIndex; i >= 0; i--) {
                    try {
                        ExecutionInterceptor interceptor = interceptors.get(i);
                        interceptor.onError(context, ex);
                    } catch (Throwable onErrorEx) {
                        XLogger.error(I18n.executionChainText.errorInInterceptorOnError, onErrorEx.getMessage());
                        XLogger.error(onErrorEx);
                    }
                }
                throw ex;
            }
        });
    }
}

package com.opcua.web.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.milo.opcua.stack.core.UaException;

public final class Futures {

    private Futures() {
    }

    public static <T> T await(CompletableFuture<T> future, long timeoutMs, String action) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException(action + "超时（" + timeoutMs + "ms）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + "被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof UaException ua) {
                String message = ua.getMessage();
                if (message == null || message.isBlank()) {
                    message = ua.getStatusCode() == null ? ua.getClass().getSimpleName() : ua.getStatusCode().toString();
                }
                throw new IllegalStateException(action + "失败: " + message, ua);
            }
            throw new IllegalStateException(action + "失败: " + cause.getMessage(), cause);
        }
    }
}

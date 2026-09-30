package com.example.gsb.graph;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation handle for a single traversal. Thread-safe:
 * {@link #cancel()} may be called from any thread at any time, including
 * before the traversal starts.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}

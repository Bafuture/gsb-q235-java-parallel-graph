package com.example.gsb.graph.internal;

/** 遍历取消令牌：在引擎启动前即绑定到句柄，避免取消信号丢失。 */
public final class CancellationToken {

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}

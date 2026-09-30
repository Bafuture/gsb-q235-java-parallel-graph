package com.example.gsb.graph;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次异步遍历的句柄。可取消；取消后工作线程会退出，已处理部分的结果仍可通过 result() 获取。
 */
public final class TraversalHandle {

    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicReference<TraversalResult> result = new AtomicReference<>();
    private volatile Runnable cancelAction;

    public void setCancelAction(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    /** 请求取消遍历（幂等）。已处理的结果不会丢失。 */
    public void cancel() {
        Runnable action = cancelAction;
        if (action != null) {
            action.run();
        }
    }

    /** 遍历是否已结束（正常完成或被取消）。 */
    public boolean isDone() {
        return done.getCount() == 0;
    }

    /** 阻塞等待遍历结束并返回结果（取消时返回部分结果）。 */
    public TraversalResult result() {
        await();
        return result.get();
    }

    public boolean await(long timeout, TimeUnit unit) {
        try {
            return done.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void await() {
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void complete(TraversalResult value) {
        result.set(value);
        done.countDown();
    }
}

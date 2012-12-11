package org.jetlang.remote.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogoutResult {
    private final AtomicBoolean logoutLatchComplete;
    private final CountDownLatch closedLatch;

    public LogoutResult(AtomicBoolean logoutLatchComplete, CountDownLatch closedLatch) {
        this.logoutLatchComplete = logoutLatchComplete;
        this.closedLatch = closedLatch;
    }

    public boolean logoutLatchComplete() {
        return logoutLatchComplete.get();
    }

    public boolean closedLatchComplete() {
        return closedLatch.getCount() == 0;
    }

    public boolean await(int time, TimeUnit unit) throws InterruptedException {
        return awaitClosedLatchOnly(time, unit) && logoutLatchComplete.get();
    }

    public boolean awaitClosedLatchOnly(int time, TimeUnit unit) throws InterruptedException {
        return closedLatch.await(time, unit);
    }
}

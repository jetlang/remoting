package org.jetlang.remote.client;

import org.jetlang.remote.core.TopicReader;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public class JetlangClientConfig {
    private int hbIntervalInMs = 2000;
    private long initialConnectDelayInMs = 0;
    private long reconnectDelayInMs = 2000;
    private long logoutTimeout = 60;
    private TimeUnit logoutTimeoutUnit = TimeUnit.SECONDS;
    private boolean cacheTopics = true;

    public void setHeartbeatIntervalInMs(int ms) {
        this.hbIntervalInMs = ms;
    }

    public int getHeartbeatIntervalInMs() {
        return hbIntervalInMs;
    }

    public long getInitialConnectDelayInMs() {
        return initialConnectDelayInMs;
    }

    public long getReconnectDelayInMs() {
        return reconnectDelayInMs;
    }

    public void setInitialConnectDelayInMs(long initialConnectDelayInMs) {
        this.initialConnectDelayInMs = initialConnectDelayInMs;
    }

    public void setReconnectDelayInMs(long reconnectDelayInMs) {
        this.reconnectDelayInMs = reconnectDelayInMs;
    }

    public long getLogoutLatchTimeout() {
        return logoutTimeout;
    }

    public TimeUnit getLogoutLatchTimeoutUnit() {
        return logoutTimeoutUnit;
    }

    public void setLogoutTimeout(long logoutTimeout) {
        this.logoutTimeout = logoutTimeout;
    }

    public void setLogoutTimeoutUnit(TimeUnit logoutTimeoutUnit) {
        this.logoutTimeoutUnit = logoutTimeoutUnit;
    }

    public boolean getCacheTopics() {
        return cacheTopics;
    }

    public void setCacheTopics(boolean cacheTopics) {
        this.cacheTopics = cacheTopics;
    }

    public TopicReader createTopicReader(Charset charset) {
        return cacheTopics ? new TopicReader.Cached(charset) : new TopicReader.Default(charset);
    }
}

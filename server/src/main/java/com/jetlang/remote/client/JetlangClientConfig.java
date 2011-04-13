package com.jetlang.remote.client;

public class JetlangClientConfig {
    private int hbIntervalInMs = 2000;
    private long initialConnectDelayInMs = 0;
    private long reconnectDelayInMs = 2000;

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
}

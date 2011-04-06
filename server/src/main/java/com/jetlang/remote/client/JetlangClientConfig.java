package com.jetlang.remote.client;

public class JetlangClientConfig {
    private long hbIntervalInMs;

    public void setHeartbeatIntervalInMs(long ms) {
        this.hbIntervalInMs = ms;
    }

    public long getHeartbeatIntervalInMs() {
        return hbIntervalInMs;
    }
}

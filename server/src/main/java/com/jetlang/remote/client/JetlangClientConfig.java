package com.jetlang.remote.client;

public class JetlangClientConfig {
    private int hbIntervalInMs = 2000;

    public void setHeartbeatIntervalInMs(int ms) {
        this.hbIntervalInMs = ms;
    }

    public int getHeartbeatIntervalInMs() {
        return hbIntervalInMs;
    }
}

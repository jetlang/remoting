package com.jetlang.remote.server;

public class JetlangSessionConfig {

    private int hbIntervalInMs = 2000;

    public void setHeartbeatIntervalInMs(int ms) {
        this.hbIntervalInMs = ms;
    }

    public int getHeartbeatIntervalInMs() {
        return hbIntervalInMs;
    }

}

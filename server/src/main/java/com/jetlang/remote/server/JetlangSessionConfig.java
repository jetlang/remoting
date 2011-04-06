package com.jetlang.remote.server;

public class JetlangSessionConfig {

    private int hbIntervalInMs = 2000;
    private boolean tcpNoDelay = true;
    private int receiveBufferSize = 1024 * 512;
    private int sendBufferSize = 1024 * 512;

    public void setHeartbeatIntervalInMs(int ms) {
        this.hbIntervalInMs = ms;
    }

    public int getHeartbeatIntervalInMs() {
        return hbIntervalInMs;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }
}

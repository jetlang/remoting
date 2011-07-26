package org.jetlang.remote.core;

public class MsgTypes {

    public static final int Heartbeat = 1;
    public static final int Disconnect = 2;
    public static final int Subscription = 3;
    public static final int Data = 4;
    public static final int Unsubscribe = 5;
    public static final int DataRequest = 6;
    public static final int DataReply = 7;

    private MsgTypes() {

    }
}

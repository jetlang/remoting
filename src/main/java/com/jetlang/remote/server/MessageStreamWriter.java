package com.jetlang.remote.server;

import java.io.IOException;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 8:48 AM
 */
public interface MessageStreamWriter {

    void writeByteAsInt(int byteToWrite) throws IOException;

    void write(String topic, Object msg) throws IOException;

    void writeRequest(int reqId, String reqTopic, Object req) throws IOException;

    void writeBytes(byte[] bytes) throws IOException;

    boolean tryClose();


    void writeReply(int reqId, String reqTopic, Object replyMsg) throws IOException;
}

package com.jetlang.remote.acceptor;

import java.io.IOException;
import java.nio.charset.Charset;

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

    void writeSubscription(int msgType, String subject, Charset charset) throws IOException;

}

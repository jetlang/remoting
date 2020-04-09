package org.jetlang.remote.acceptor;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 8:48 AM
 */
public interface MessageStreamWriter<T> {

    void writeByteAsInt(int byteToWrite) throws IOException;

    void write(String topic, T msg) throws IOException;

    void writeRequest(int reqId, String reqTopic, T req) throws IOException;

    void writeBytes(byte[] bytes) throws IOException;

    boolean tryClose();

    void writeReply(int reqId, String reqTopic, T replyMsg) throws IOException;

    void writeSubscription(int msgType, String subject, Charset charset) throws IOException;

}

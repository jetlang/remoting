package org.jetlang.remote.client;

import org.jetlang.remote.core.JetlangBuffer;
import org.jetlang.remote.core.ObjectByteWriter;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * A direct buffer that resizes automatically. Can be used to batch sends.
 */
public class SendBuffer {

    private final JetlangBuffer buffer;

    public SendBuffer(int initialSize){
        this.buffer = new JetlangBuffer(initialSize);
    }

    public ByteBuffer getBuffer() {
        return buffer.getBuffer();
    }

    public void appendMsg(byte[] topicBytes, ByteBuffer msg){
        this.buffer.appendMsg(topicBytes, msg);
    }

    public <T> void appendMsg(String topic, T msg, ObjectByteWriter<T> objWriter, Charset topicCharSet){
        this.buffer.appendMsg(topic, msg, objWriter, topicCharSet);
    }

    public <T> void appendRequest(int reqId, String reqTopic, T reqMsg, ObjectByteWriter<T> objectByteWriter, Charset topicCharset){
        this.buffer.appendRequest(reqId, reqTopic, reqMsg, objectByteWriter, topicCharset);
    }

    public void appendRequest(int reqId, byte[] reqTopic, ByteBuffer msg){
        this.buffer.appendRequest(reqId, reqTopic, msg);
    }

    public void clear() {
        this.buffer.clear();
    }
}

package org.jetlang.remote.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class JetlangBuffer {
    private ByteBuffer buffer;
    private final ByteMessageWriter byteMsgWriter = new ByteMessageWriter() {
        @Override
        public void writeObjectAsBytes(byte[] buffer, int offset, int length) {
                appendInt(length);
                append(buffer, offset, length);
        }

        @Override
        public void writeObjectAsBytes(ByteBuffer buffer) {
            appendInt(buffer.remaining());
            append(buffer);
        }
    };

    public ByteBuffer getBuffer(){
        return buffer;
    }

    public JetlangBuffer(int initialSize) {
        this.buffer = allocate(initialSize);
    }

    private static ByteBuffer allocate(int initialSize) {
        return ByteBuffer.allocateDirect(initialSize).order(ByteOrder.BIG_ENDIAN);
    }

    public <T> void appendMsg(String topic, T msg, ObjectByteWriter<T> objWriter, Charset charset) {
        appendMsg(topic, topic.getBytes(charset), msg, objWriter);
    }

    public <T> void appendMsg(String topic, byte[] topicBytes, T msg, ObjectByteWriter<T> objWriter) {
        appendIntAsByte(MsgTypes.Data);
        appendTopic(topicBytes);
        writeMsgOnly(topic, msg, objWriter);
    }

    public <T> void writeMsgOnly(String topic, T msg, ObjectByteWriter<T> objWriter) {
        objWriter.write(topic, msg, byteMsgWriter);
    }

    public <T> void appendMsg(byte[] topicBytes, ByteBuffer msg) {
        int sz = msg.remaining();
        resize(1 + 1 + topicBytes.length + 4 + sz);
        appendIntAsByte(MsgTypes.Data);
        appendTopic(topicBytes);
        appendInt(sz);
        buffer.put(msg);
    }

    private <T> void appendMsgBody(String topic, T msg, ObjectByteWriter<T> objWriter, Charset charset) {
        byte[] topicBytes = topic.getBytes(charset);
        appendTopic(topicBytes);
        objWriter.write(topic, msg, byteMsgWriter);
    }

    public void appendTopic(byte[] topicBytes) {
        appendIntAsByte(topicBytes.length);
        append(topicBytes, 0, topicBytes.length);
    }

    public int position() {
        return buffer.position();
    }

    private void append(byte[] topicBytes, int offset, int length) {
        resize(length);
        buffer.put(topicBytes, offset, length);
    }

    private void append(ByteBuffer msg) {
        resize(msg.remaining());
        buffer.put(msg);
    }


    public void appendIntAsByte(int msgType) {
        resize(1);
        buffer.put((byte) msgType);
    }

    private void appendInt(int value) {
        resize(4);
        buffer.putInt(value);
    }

    private void resize(int required) {
        if (buffer.remaining() < required) {
            ByteBuffer resized = allocate(buffer.capacity() + Math.max(required, 128));
            buffer.flip();
            resized.put(buffer);
            this.buffer = resized;
        }
    }

    public void appendSubscription(String subject, int subscriptionType, Charset charset) {
        byte[] bytes = subject.getBytes(charset);
        appendIntAsByte(subscriptionType);
        appendTopic(bytes);
    }

    public void appendBytes(byte[] bytes) {
        append(bytes, 0, bytes.length);
    }

    public <T> void appendRequest(int reqId, String reqTopic, T reqMsg, ObjectByteWriter<T> objectByteWriter, Charset topicCharset) {
        appendIntAsByte(MsgTypes.DataRequest);
        appendInt(reqId);
        appendMsgBody(reqTopic, reqMsg, objectByteWriter, topicCharset);
    }

    public <T> void appendRequest(int reqId, byte[] reqTopic, ByteBuffer reqMsg) {
        int sz = reqMsg.remaining();
        resize(1 + 4 + 1 + reqTopic.length + 4 + sz);
        appendIntAsByte(MsgTypes.DataRequest);
        appendInt(reqId);
        appendTopic(reqTopic);
        appendInt(sz);
        buffer.put(reqMsg);
    }


    public <T> void appendReply(int reqId, String replyTopic, T replyMsg, ObjectByteWriter<T> objectByteWriter, Charset topicCharset) {
        appendIntAsByte(MsgTypes.DataReply);
        appendInt(reqId);
        appendMsgBody(replyTopic, replyMsg, objectByteWriter, topicCharset);
    }

    public void flip() {
        buffer.flip();
    }

    public void clear(){
        buffer.clear();
    }
}

package org.jetlang.remote.client;

import org.jetlang.remote.core.ByteMessageWriter;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.web.SendResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class JetlangDirectBuffer {
    public ByteBuffer buffer;
    private final ByteMessageWriter byteMsgWriter = (buffer, offset, length) -> {
        appendInt(length);
        append(buffer, offset, length);
    };

    public JetlangDirectBuffer(int initialSize) {
        this.buffer = allocate(initialSize);
    }

    private static ByteBuffer allocate(int initialSize) {
        return ByteBuffer.allocateDirect(initialSize).order(ByteOrder.BIG_ENDIAN);
    }

    public <T> SendResult write(String topic, T msg, ObjectByteWriter<T> objWriter, TcpClientNioFiber.Writer chan, Charset charset) {
        append(topic, msg, objWriter, charset);
        return flush(chan);
    }

    public <T> void append(String topic, T msg, ObjectByteWriter<T> objWriter, Charset charset) {
        appendIntAsByte(MsgTypes.Data);
        byte[] topicBytes = topic.getBytes(charset);
        appendIntAsByte(topicBytes.length);
        append(topicBytes, 0, topicBytes.length);
        objWriter.write(topic, msg, byteMsgWriter);
    }

    public int position(){
        return buffer.position();
    }

    private SendResult flush(TcpClientNioFiber.Writer chan) {
        buffer.flip();
        SendResult result = chan.write(buffer);
        buffer.clear();
        return result;
    }

    private void append(byte[] topicBytes, int offset, int length) {
        resize(length);
        buffer.put(topicBytes, offset, length);
    }

    public SendResult writeMsgType(int msgType, TcpClientNioFiber.Writer writer) {
        appendIntAsByte(msgType);
        return flush(writer);
    }

    public void appendIntAsByte(int msgType) {
        resize(1);
        buffer.put((byte) msgType);
    }

    public void appendInt(int value) {
        resize(4);
        buffer.putInt(value);
    }

    private void resize(int required) {
        if (buffer.remaining() < required) {
            ByteBuffer resized = allocate(buffer.capacity() + (required * 2));
            buffer.flip();
            resized.put(buffer);
            this.buffer = resized;
        }
    }

    public SendResult writeSubscription(String subject, int subscriptionType, Charset charset, TcpClientNioFiber.Writer writer) {
        byte[] bytes = subject.getBytes(charset);
        appendIntAsByte(subscriptionType);
        appendIntAsByte(bytes.length);
        append(bytes, 0, bytes.length);
        return flush(writer);
    }

    public void appendBytes(byte[] bytes) {
        append(bytes, 0, bytes.length);
    }
}

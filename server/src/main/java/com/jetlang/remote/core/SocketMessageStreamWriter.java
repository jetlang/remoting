package com.jetlang.remote.core;

import com.jetlang.remote.acceptor.MessageStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 8:52 AM
 */
public class SocketMessageStreamWriter implements MessageStreamWriter {
    private final ClosableOutputStream socket;
    private final Charset charset;
    private final ObjectByteWriter writer;
    private final ByteArrayBuffer buffer;
    private final OutputStream socketOutputStream;

    public SocketMessageStreamWriter(ClosableOutputStream socket, Charset charset, ObjectByteWriter writer) throws IOException {
        this.socket = socket;
        this.charset = charset;
        this.writer = writer;
        this.socketOutputStream = socket.getOutputStream();
        this.buffer = new ByteArrayBuffer();
    }

    public void writeByteAsInt(int byteToWrite) throws IOException {
        //write directly. no need for buffer.
        socketOutputStream.write(byteToWrite);
    }

    public void writeSubscription(int msgType, String subject, Charset charset) throws IOException {
        byte[] bytes = subject.getBytes(charset);
        buffer.appendIntAsByte(msgType);
        buffer.appendIntAsByte(bytes.length);
        buffer.append(bytes);
        buffer.flushTo(socketOutputStream);
    }

    public boolean tryClose() {
        return socket.close();
    }

    private final ByteMessageWriter byteMessageWriter = new ByteMessageWriter() {
        public void writeObjectAsBytes(byte[] data, int offset, int length) {
            buffer.appendInt(length);
            buffer.append(data, offset, length);
        }
    };

    public void write(String topic, Object msg) throws IOException {
        buffer.appendIntAsByte(MsgTypes.Data);
        writeData(topic, msg);
    }

    public void writeRequest(int id, String reqTopic, Object req) throws IOException {
        buffer.appendIntAsByte(MsgTypes.DataRequest);
        buffer.appendInt(id);
        writeData(reqTopic, req);
    }

    public void writeReply(int reqId, String requestTopic, Object replyMsg) throws IOException {
        buffer.appendIntAsByte(MsgTypes.DataReply);
        buffer.appendInt(reqId);
        writeData(requestTopic, replyMsg);
    }

    private void writeData(String topic, Object req) throws IOException {
        byte[] topicBytes = topic.getBytes(charset);
        buffer.appendIntAsByte(topicBytes.length);
        buffer.append(topicBytes);
        writer.write(topic, req, byteMessageWriter);
        buffer.flushTo(socketOutputStream);
    }

    public void writeBytes(byte[] bytes) throws IOException {
        //no need to buffer.
        socket.getOutputStream().write(bytes);
    }

}

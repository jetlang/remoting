package com.jetlang.remote.core;

import com.jetlang.remote.acceptor.MessageStreamWriter;

import java.io.DataOutputStream;
import java.io.IOException;
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
    private final DataOutputStream dataStream;

    public SocketMessageStreamWriter(ClosableOutputStream socket, Charset charset, ObjectByteWriter writer) throws IOException {
        this.socket = socket;
        this.charset = charset;
        this.writer = writer;
        this.dataStream = new DataOutputStream(socket.getOutputStream());
    }

    public void writeByteAsInt(int byteToWrite) throws IOException {
        socket.getOutputStream().write(byteToWrite);
    }

    public boolean tryClose() {
        return socket.close();
    }

    private final ByteMessageWriter byteMessageWriter = new ByteMessageWriter() {
        public void writeObjectAsBytes(byte[] buffer, int offset, int length) {
            try {
                dataStream.writeInt(length);
                dataStream.write(buffer, offset, length);
            } catch (IOException e) {
                tryClose();
            }
        }
    };

    public void write(String topic, Object msg) throws IOException {
        socket.getOutputStream().write(MsgTypes.Data);
        writeData(topic, msg);
    }

    public void writeRequest(int id, String reqTopic, Object req) throws IOException {
        socket.getOutputStream().write(MsgTypes.DataRequest);
        dataStream.writeInt(id);
        writeData(reqTopic, req);
    }

    public void writeReply(int reqId, String requestTopic, Object replyMsg) throws IOException {
        socket.getOutputStream().write(MsgTypes.DataReply);
        dataStream.writeInt(reqId);
        writeData(requestTopic, replyMsg);
    }

    private void writeData(String topic, Object req) throws IOException {
        byte[] topicBytes = topic.getBytes(charset);
        socket.getOutputStream().write(topicBytes.length);
        socket.getOutputStream().write(topicBytes);
        writer.write(topic, req, byteMessageWriter);
    }

    public void writeBytes(byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
    }

}

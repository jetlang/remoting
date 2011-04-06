package com.jetlang.remote.core;

import com.jetlang.remote.server.MessageStreamWriter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 8:52 AM
 */
public class SocketMessageStreamWriter implements MessageStreamWriter {
    private final Socket socket;
    private final Charset charset;
    private final ObjectByteWriter writer;
    private final DataOutputStream dataStream;

    public SocketMessageStreamWriter(Socket socket, Charset charset, ObjectByteWriter writer) throws IOException {
        this.socket = socket;
        this.charset = charset;
        this.writer = writer;
        this.dataStream = new DataOutputStream(socket.getOutputStream());
    }

    public void writeByteAsInt(int byteToWrite) {
        try {
            socket.getOutputStream().write(byteToWrite);
        } catch (IOException e) {
            tryClose();
        }
    }

    private void tryClose() {
        try {
            if (!socket.isClosed())
                socket.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }
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

    public void write(String topic, Object msg) {
        byte[] topicBytes = topic.getBytes(charset);
        try {
            socket.getOutputStream().write(MsgTypes.Data);
            socket.getOutputStream().write(topicBytes.length);
            socket.getOutputStream().write(topicBytes);
            writer.write(msg, byteMessageWriter);
        } catch (IOException e) {
            tryClose();
        }
    }

}

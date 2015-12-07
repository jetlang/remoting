package org.jetlang.remote.core;

import org.jetlang.remote.acceptor.MessageStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 8:52 AM
 */
public class SocketMessageStreamWriter implements MessageStreamWriter {
    private final Charset charset;
    private final ObjectByteWriter writer;
    private final ByteArrayBuffer buffer;
    private final Out socketOutputStream;

    public SocketMessageStreamWriter(Out socket, Charset charset, ObjectByteWriter writer) {
        this.charset = charset;
        this.writer = writer;
        this.socketOutputStream = socket;
        this.buffer = socketOutputStream.getBuffer();
    }

    public SocketMessageStreamWriter(ClosableOutputStream socket, Charset charset, ObjectByteWriter writer) throws IOException {
        this(new BufferedStream(new ByteArrayBuffer(), socket), charset, writer);
    }

    public void writeByteAsInt(int byteToWrite) throws IOException {
        socketOutputStream.write(byteToWrite);
    }

    public void writeSubscription(int msgType, String subject, Charset charset) throws IOException {
        byte[] bytes = subject.getBytes(charset);
        buffer.appendIntAsByte(msgType);
        buffer.appendIntAsByte(bytes.length);
        buffer.append(bytes);
        socketOutputStream.flush();
    }

    public boolean tryClose() {
        return socketOutputStream.close();
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

    public int writeWithoutFlush(String topic, Object msg) throws IOException {
        buffer.appendIntAsByte(MsgTypes.Data);
        writeIntoBuffer(topic, msg);
        return buffer.position;
    }

    public void setPositionAndFlush(int position) throws IOException {
        buffer.position = position;
        socketOutputStream.flush();
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
        writeIntoBuffer(topic, req);
        socketOutputStream.flush();
    }

    public void writeIntoBuffer(String topic, Object req) throws IOException {
        byte[] topicBytes = topic.getBytes(charset);
        buffer.appendIntAsByte(topicBytes.length);
        buffer.append(topicBytes);
        writer.write(topic, req, byteMessageWriter);
    }

    public void writeBytes(byte[] bytes) throws IOException {
        socketOutputStream.writeBytes(bytes);
    }

    public interface Out {
        ByteArrayBuffer getBuffer();

        void flush() throws IOException;

        void write(int byteToWrite) throws IOException;

        void writeBytes(byte[] bytes) throws IOException;

        boolean close();
    }

    public static class BufferedStream implements Out {
        private final ByteArrayBuffer buffer;
        private final ClosableOutputStream closable;
        private final OutputStream output;

        public BufferedStream(ByteArrayBuffer buffer, ClosableOutputStream closable) throws IOException {
            this.buffer = buffer;
            this.closable = closable;
            this.output = closable.getOutputStream();
        }

        public ByteArrayBuffer getBuffer() {
            return buffer;
        }

        public void flush() throws IOException {
            buffer.flushTo(closable.getOutputStream());
        }

        public void write(int byteToWrite) throws IOException {
            //write directly. no need for buffer.
            output.write(byteToWrite);
        }

        public void writeBytes(byte[] bytes) throws IOException {
            //write directly. no need for buffer.
            output.write(bytes);
        }

        public boolean close() {
            return closable.close();
        }
    }

}

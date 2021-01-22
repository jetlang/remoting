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
public class SocketMessageStreamWriter<T> implements MessageStreamWriter<T> {
    private final Charset charset;
    private final ObjectByteWriter<T> writer;
    private final ByteArrayBuffer buffer;
    private final Out socketOutputStream;

    public SocketMessageStreamWriter(Out socket, Charset charset, ObjectByteWriter<T> writer) {
        this.charset = charset;
        this.writer = writer;
        this.socketOutputStream = socket;
        this.buffer = socketOutputStream.getBuffer();
    }

    public SocketMessageStreamWriter(ClosableOutputStream socket, Charset charset, ObjectByteWriter<T> writer) throws IOException {
        this(new BufferedStream(new ByteArrayBuffer(), socket), charset, writer);
    }

    @Override
    public void writeByteAsInt(int byteToWrite) throws IOException {
        socketOutputStream.write(byteToWrite);
    }

    @Override
    public void writeSubscription(int msgType, String subject, Charset charset) throws IOException {
        byte[] bytes = subject.getBytes(charset);
        buffer.appendIntAsByte(msgType);
        buffer.appendIntAsByte(bytes.length);
        buffer.append(bytes);
        socketOutputStream.flush();
    }

    @Override
    public boolean tryClose() {
        return socketOutputStream.close();
    }

    private final ByteMessageWriter byteMessageWriter = new ByteMessageWriter() {
        public void writeObjectAsBytes(byte[] data, int offset, int length) {
            buffer.appendInt(length);
            buffer.append(data, offset, length);
        }
    };

    @Override
    public void write(String topic, T msg) throws IOException {
        buffer.appendIntAsByte(MsgTypes.Data);
        writeData(topic, msg);
    }

    @Override
    public void writeRequest(int id, String reqTopic, T req) throws IOException {
        buffer.appendIntAsByte(MsgTypes.DataRequest);
        buffer.appendInt(id);
        writeData(reqTopic, req);
    }

    @Override
    public void writeReply(int reqId, String requestTopic, T replyMsg) throws IOException {
        buffer.appendIntAsByte(MsgTypes.DataReply);
        buffer.appendInt(reqId);
        writeData(requestTopic, replyMsg);
    }

    private void writeData(String topic, T req) throws IOException {
        writeIntoBuffer(topic, req);
        socketOutputStream.flush();
    }

    public void writeIntoBuffer(String topic, T req) throws IOException {
        byte[] topicBytes = topic.getBytes(charset);
        buffer.appendIntAsByte(topicBytes.length);
        buffer.append(topicBytes);
        writer.write(topic, req, byteMessageWriter);
    }

    @Override
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

        @Override
        public ByteArrayBuffer getBuffer() {
            return buffer;
        }

        @Override
        public void flush() throws IOException {
            buffer.flushTo(closable.getOutputStream());
        }

        @Override
        public void write(int byteToWrite) throws IOException {
            //write directly. no need for buffer.
            output.write(byteToWrite);
        }

        @Override
        public void writeBytes(byte[] bytes) throws IOException {
            //write directly. no need for buffer.
            output.write(bytes);
        }

        @Override
        public boolean close() {
            return closable.close();
        }
    }

}

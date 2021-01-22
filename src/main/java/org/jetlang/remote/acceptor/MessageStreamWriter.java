package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.JetlangBuffer;
import org.jetlang.remote.core.ObjectByteWriter;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

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

    class NioChannel<T> implements MessageStreamWriter<T>{
        private final SocketChannel channel;
        private final Charset charset;
        private final ObjectByteWriter<T> writer;
        private final JetlangBuffer buffer = new JetlangBuffer(128);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        public NioChannel(SocketChannel socket, Charset charset, ObjectByteWriter<T> writer) {
            this.channel = socket;
            this.charset = charset;
            this.writer = writer;
        }

        @Override
        public void writeByteAsInt(int byteToWrite) throws IOException {
            buffer.appendIntAsByte(byteToWrite);
            flush();
        }

        private void flush() throws IOException {
            buffer.flip();
            this.channel.write(buffer.getBuffer());
            buffer.clear();
        }

        @Override
        public void write(String topic, T msg) throws IOException {
            buffer.appendMsg(topic, msg, writer, charset);
            flush();
        }

        @Override
        public void writeRequest(int reqId, String reqTopic, T req) throws IOException {
            buffer.appendRequest(reqId, reqTopic, req, writer, charset);
            flush();
        }

        @Override
        public void writeBytes(byte[] bytes) throws IOException {
            buffer.appendBytes(bytes);
            flush();
        }

        @Override
        public boolean tryClose() {
            try {
                if(closed.compareAndSet(false, true)) {
                    channel.close();
                    return true;
                }
                return false;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public void writeReply(int reqId, String reqTopic, T replyMsg) throws IOException {
            buffer.appendReply(reqId, reqTopic, replyMsg, writer, charset);
            flush();
        }

        @Override
        public void writeSubscription(int msgType, String subject, Charset charset) throws IOException {
            buffer.appendSubscription(subject, msgType, charset);
            flush();
        }
    }

}

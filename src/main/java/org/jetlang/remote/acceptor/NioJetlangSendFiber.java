package org.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.core.ByteArrayBuffer;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.SocketMessageStreamWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NioJetlangSendFiber {

    private final Fiber sendFiber;
    private final Writer writer;
    private final SocketMessageStreamWriter stream;
    private final Buffer buffer;
    private final List<ChannelState> sessions = new ArrayList<>();

    public NioJetlangSendFiber(Fiber sendFiber, NioFiber receiveFiber, ObjectByteWriter objectByteWriter, Charset charset, NioFiberImpl.OnBuffer ob) {
        this.sendFiber = sendFiber;
        this.buffer = new Buffer(receiveFiber, sendFiber, ob);
        this.writer = new Writer(buffer);
        this.stream = new SocketMessageStreamWriter(this.writer, charset, objectByteWriter);
    }

    public void onNewSession(ChannelState channel) {
        sendFiber.execute(() -> sessions.add(channel));
    }

    private class BulkPublish implements Runnable {
        private final String topic;
        private final Object object;
        private int position = -1;

        public BulkPublish(String topic, Object object) {
            this.topic = topic;
            this.object = object;
        }

        @Override
        public void run() {
            position = writeToAllSubscribedClients(topic, object);
        }

        @Override
        public String toString() {
            return "BulkPublish{" +
                    "topic='" + topic + '\'' +
                    ", object=" + object.getClass() +
                    ", size=" + position +
                    '}';
        }
    }

    /**
     * Assumes the caller is already on the send thread. No check is made to ensure this, so use carefully.
     *
     * @return the number of bytes written or -1 if no sessions subscribed.
     */
    public int writeToAllSubscribedClients(String topic, Object object) {
        int position = -1;
        for (int i = 0; i < sessions.size(); i++) {
            final ChannelState channelState = sessions.get(i);
            if (channelState.subscriptions.contains(topic)) {
                set(channelState);
                try {
                    if (position == -1) {
                        position = stream.writeWithoutFlush(topic, object);
                    }
                    stream.setPositionAndFlush(position);
                } catch (IOException failed) {
                    handleDisconnect(failed, channelState);
                }
            }
        }
        return position;
    }

    public void publishToAllSubscribedClients(String topic, Object obj) {
        sendFiber.execute(new BulkPublish(topic, obj));
    }

    public static class ChannelState {
        private final SocketChannel channel;
        private final NioJetlangRemotingClientFactory.Id id;
        private final NioFiber fiber;
        private BufferState buffer;
        private final Set<String> subscriptions = new HashSet<>();

        public ChannelState(SocketChannel channel, NioJetlangRemotingClientFactory.Id id, NioFiber fiber) {
            this.channel = channel;
            this.id = id;
            this.fiber = fiber;
        }

        private void close(NioControls controls) {
            controls.close(channel);
        }

        public void closeOnNioFiber() {
            fiber.execute(this::close);
        }

        @Override
        public String toString() {
            return id.toString();
        }

        private void safeCloseAndLog(IOException e) {
            closeOnNioFiber();
        }
    }

    public void sendIntAsByte(ChannelState channel, int heartbeat) {
        sendFiber.execute(() -> writeIntAsByte(channel, heartbeat));
    }

    private void writeIntAsByte(ChannelState channel, int heartbeat) {
        try {
            set(channel);
            stream.writeByteAsInt(heartbeat);
        } catch (IOException e) {
            handleDisconnect(e, channel);
        }
    }

    private void set(ChannelState channel) {
        writer.channel = channel;
        buffer.session = channel;
    }

    public void onSubscriptionRequest(String topic, ChannelState sc) {
        sendFiber.execute(() -> sc.subscriptions.add(topic));
    }

    public void onUnsubscribeRequest(String topic, ChannelState sc) {
        sendFiber.execute(() -> sc.subscriptions.remove(topic));
    }

    public <T> void publish(ChannelState sc, String topic, T msg) {
        sendFiber.execute(new Runnable() {
            @Override
            public void run() {
                if (sc.subscriptions.contains(topic)) {
                    write(sc, topic, msg);
                }
            }

            @Override
            public String toString() {
                return "Publish(" + topic + ")";
            }
        });
    }

    private <T> void write(ChannelState channel, String topic, T msg) {
        set(channel);
        try {
            stream.write(topic, msg);
        } catch (IOException e) {
            handleDisconnect(e, channel);
        }
    }

    private void writeBytes(ChannelState channel, byte[] msg) {
        set(channel);
        try {
            stream.writeBytes(msg);
        } catch (IOException e) {
            handleDisconnect(e, channel);
        }
    }


    private void handleDisconnect(IOException e, ChannelState sc) {
        sc.safeCloseAndLog(e);
        sc.buffer = null;
        removeSubscriptions(sc);
    }

    public void reply(ChannelState sc, int reqId, String replyTopic, Object replyMsg) {
        sendFiber.execute(() -> {
            set(sc);
            try {
                stream.writeReply(reqId, replyTopic, replyMsg);
            } catch (IOException e) {
                handleDisconnect(e, sc);
            }
        });
    }

    public void publishIfSubscribed(ChannelState sc, String topic, byte[] data) {
        sendFiber.execute(() -> {
            if (sc.subscriptions.contains(topic)) {
                writeBytes(sc, data);
            }
        });
    }

    public void publishBytes(ChannelState channel, byte[] data) {
        sendFiber.execute(() -> writeBytes(channel, data));
    }

    public void handleLogout(ChannelState channel) {
        sendFiber.execute(() -> {
                    writeIntAsByte(channel, MsgTypes.Disconnect);
                    removeSubscriptions(channel);
                }
        );
    }

    private void removeSubscriptions(ChannelState channel) {
        channel.subscriptions.clear();
        sessions.remove(channel);
    }

    public void handleClose(ChannelState sc) {
        sendFiber.execute(() -> {
            removeSubscriptions(sc);
            sc.buffer = null;
        });
    }

    private static class BufferState implements NioChannelHandler {

        private final SocketChannel sc;
        private final NioFiber fiber;
        private final Fiber sendFiber;
        private final ChannelState buffered;
        private final NioFiberImpl.OnBuffer onBuffer;
        private ByteBuffer b;

        public BufferState(SocketChannel sc, NioFiber fiber, Fiber sendFiber, ChannelState buffered, NioFiberImpl.OnBuffer onBuffer) {
            this.sc = sc;
            this.fiber = fiber;
            this.sendFiber = sendFiber;
            this.buffered = buffered;
            this.onBuffer = onBuffer;
            fiber.addHandler(this);
        }

        public void add(ByteBuffer byteBuffer) {
            b = NioFiberImpl.addTo(b, byteBuffer);
            onBuffer.onBuffer(sc, b);
        }

        @Override
        public boolean onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
            sendFiber.execute(this::flush);
            return false;
        }

        private void flush() {
            try {
                Buffer.tryWrite(sc, b);
                if (b.remaining() > 0) {
                    fiber.addHandler(this);
                } else {
                    buffered.buffer = null;
                    onBuffer.onBufferEnd(sc);
                }
            } catch (IOException e) {
                buffered.buffer = null;
                buffered.safeCloseAndLog(e);
            }
        }

        @Override
        public SelectableChannel getChannel() {
            return sc;
        }

        @Override
        public int getInterestSet() {
            return SelectionKey.OP_WRITE;
        }

        @Override
        public void onEnd() {
        }

        @Override
        public void onSelectorEnd() {
        }
    }

    private static class Buffer extends ByteArrayBuffer {

        private final NioFiber nioFiber;
        private final Fiber sendFiber;
        private final NioFiberImpl.OnBuffer onBuffer;
        public ChannelState session;
        private ByteBuffer byteBuffer;

        public Buffer(NioFiber nioFiber, Fiber sendFiber, NioFiberImpl.OnBuffer onBuffer) {
            this.nioFiber = nioFiber;
            this.sendFiber = sendFiber;
            this.onBuffer = onBuffer;
            this.byteBuffer = ByteBuffer.wrap(buffer);
        }

        @Override
        protected void afterResize() {
            super.afterResize();
            this.byteBuffer = ByteBuffer.wrap(buffer);
        }

        public void flush() {
            byteBuffer.position(0);
            byteBuffer.limit(position);
            position = 0;
            final SocketChannel channel = session.channel;
            BufferState st = session.buffer;
            if (st != null) {
                if (channel.isOpen())
                    st.add(byteBuffer);
                else
                    session.buffer = null;
                return;
            }
            try {
                tryWrite(channel, byteBuffer);
                if (byteBuffer.remaining() > 0) {
                    if (channel.isOpen()) {
                        final BufferState value = new BufferState(channel, nioFiber, sendFiber, session, onBuffer);
                        value.add(byteBuffer);
                        session.buffer = value;
                    }
                }
            } catch (IOException e) {
                session.safeCloseAndLog(e);
            }
        }

        public static void tryWrite(WritableByteChannel channel, ByteBuffer byteBuffer) throws IOException {
            int write;
            do {
                write = channel.write(byteBuffer);
            } while (write > 0 && byteBuffer.remaining() > 0);
        }

        public void writeSingleByte(int byteToWrite) {
            buffer[position] = (byte) byteToWrite;
            position++;
            flush();
        }

        public void writeBytes(byte[] bytes) {
            System.arraycopy(bytes, 0, buffer, 0, bytes.length);
            position += bytes.length;
            flush();
        }
    }

    private static class Writer implements SocketMessageStreamWriter.Out {

        private final Buffer buffer;
        public ChannelState channel;

        public Writer(Buffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public ByteArrayBuffer getBuffer() {
            return buffer;
        }

        @Override
        public void flush() throws IOException {
            buffer.flush();
        }

        @Override
        public void write(int byteToWrite) throws IOException {
            buffer.writeSingleByte(byteToWrite);
        }

        @Override
        public void writeBytes(byte[] bytes) throws IOException {
            buffer.writeBytes(bytes);
        }

        @Override
        public boolean close() {
            channel.closeOnNioFiber();
            return false;
        }
    }


}

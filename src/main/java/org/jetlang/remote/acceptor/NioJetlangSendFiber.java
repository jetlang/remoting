package org.jetlang.remote.acceptor;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.client.JetlangDirectBuffer;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ObjectByteWriter;

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
import java.util.concurrent.TimeUnit;

public class NioJetlangSendFiber<T> {

    private final Fiber sendFiber;
    private final Buffer<T> buffer;
    private final List<ChannelState> sessions = new ArrayList<>();

    public NioJetlangSendFiber(Fiber sendFiber, NioFiber receiveFiber, ObjectByteWriter<T> objectByteWriter, Charset charset, NioFiberImpl.OnBuffer ob) {
        this.sendFiber = sendFiber;
        this.buffer = new Buffer<>(receiveFiber, sendFiber, ob, objectByteWriter, charset);
    }

    public Fiber getFiber() {
        return sendFiber;
    }

    public void onNewSession(ChannelState channel) {
        sendFiber.execute(() -> sessions.add(channel));
    }

    private class BulkPublish implements Runnable {
        private final String topic;
        private final T object;
        private int position = -1;

        public BulkPublish(String topic, T object) {
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
    public int writeToAllSubscribedClients(String topic, T object) {
        int position = -1;
        for (int i = 0; i < sessions.size(); i++) {
            final ChannelState channelState = sessions.get(i);
            if (channelState.subscriptions.contains(topic)) {
                if (position == -1) {
                    buffer.append(topic, object);
                    position = buffer.position();
                }
                buffer.setPositionAndFlush(position, channelState);
            }
        }
        return position;
    }

    public void publishToAllSubscribedClients(String topic, T obj) {
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
    }

    public void sendIntAsByte(ChannelState channel, int heartbeat) {
        sendFiber.execute(() -> writeIntAsByte(channel, heartbeat));
    }

    private void writeIntAsByte(ChannelState channel, int heartbeat) {
        buffer.writeSingleByte(heartbeat, channel);
    }


    public void onSubscriptionRequest(String topic, ChannelState sc) {
        sendFiber.execute(() -> sc.subscriptions.add(topic));
    }

    public void onUnsubscribeRequest(String topic, ChannelState sc) {
        sendFiber.execute(() -> sc.subscriptions.remove(topic));
    }

    public void publish(ChannelState sc, String topic, T msg) {
        sendFiber.execute(new Runnable() {
            @Override
            public void run() {
                if (sc.subscriptions.contains(topic)) {
                    buffer.write(topic, msg, sc);
                }
            }

            @Override
            public String toString() {
                return "Publish(" + topic + ")";
            }
        });
    }



    public void reply(ChannelState sc, int reqId, String replyTopic, T replyMsg) {
        sendFiber.execute(() -> {
            buffer.writeReply(reqId, replyTopic, replyMsg, sc);
        });
    }

    public void publishIfSubscribed(ChannelState sc, String topic, byte[] data) {
        sendFiber.execute(() -> {
            if (sc.subscriptions.contains(topic)) {
                buffer.writeBytes(data, sc);
            }
        });
    }

    public void publishBytes(ChannelState channel, byte[] data) {
        sendFiber.execute(() -> buffer.writeBytes(data, channel));
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
        public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
            sendFiber.execute(this::flush);
            return Result.RemoveHandler;
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
                buffered.closeOnNioFiber();
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

    private static class Buffer<T> {

        private final NioFiber nioFiber;
        private final Fiber sendFiber;
        private final NioFiberImpl.OnBuffer onBuffer;
        private final ObjectByteWriter<T> objectByteWriter;
        private final Charset charset;
        private final JetlangDirectBuffer byteBuffer;

        public Buffer(NioFiber nioFiber, Fiber sendFiber, NioFiberImpl.OnBuffer onBuffer, ObjectByteWriter<T> objectByteWriter, Charset charset) {
            this.nioFiber = nioFiber;
            this.sendFiber = sendFiber;
            this.onBuffer = onBuffer;
            this.objectByteWriter = objectByteWriter;
            this.charset = charset;
            this.byteBuffer = new JetlangDirectBuffer(1024);
        }

        public void flush(ChannelState session) {
            ByteBuffer toSend = byteBuffer.buffer;
            toSend.flip();
            executeFlush(toSend, session);
            toSend.clear();
        }

        private void executeFlush(ByteBuffer toSend, ChannelState session) {
            final SocketChannel channel = session.channel;
            BufferState st = session.buffer;
            if (st != null) {
                if (channel.isOpen())
                    st.add(toSend);
                else
                    session.buffer = null;
                return;
            }
            try {
                tryWrite(channel, toSend);
                if (toSend.remaining() > 0) {
                    if (channel.isOpen()) {
                        final BufferState value = new BufferState(channel, nioFiber, sendFiber, session, onBuffer);
                        value.add(toSend);
                        session.buffer = value;
                    }
                }
            } catch (IOException e) {
                session.closeOnNioFiber();
            }
        }

        public void append(String topic, T object) {
            byteBuffer.append(topic, object, objectByteWriter, charset);
        }

        public void write(String topic, T msg, ChannelState channel) {
            write(topic, msg, objectByteWriter, charset, channel);
        }

        public void writeReply(int reqId, String replyTopic, T replyMsg, ChannelState channel) {
            writeReply(reqId, replyTopic, replyMsg, objectByteWriter, charset, channel);
        }

        public static void tryWrite(WritableByteChannel channel, ByteBuffer byteBuffer) throws IOException {
            int write;
            do {
                write = channel.write(byteBuffer);
            } while (write > 0 && byteBuffer.remaining() > 0);
        }

        public void writeSingleByte(int byteToWrite, ChannelState session) {
            byteBuffer.appendIntAsByte(byteToWrite);
            flush(session);
        }

        public void writeBytes(byte[] bytes, ChannelState session) {
            byteBuffer.appendBytes(bytes);
            flush(session);
        }

        public void setPositionAndFlush(int position, ChannelState session) {
            ByteBuffer bb = byteBuffer.buffer;
            bb.position(position);
            flush(session);
        }

        public int position() {
            return byteBuffer.position();
        }

        public <T> void write(String topic, T msg, ObjectByteWriter<T> objectByteWriter, Charset charset, ChannelState session) {
            byteBuffer.append(topic, msg, objectByteWriter, charset);
            flush(session);
        }

        public <T> void writeReply(int reqId, String replyTopic, T replyMsg, ObjectByteWriter<T> objectByteWriter, Charset charset, ChannelState session) {
            byteBuffer.appendIntAsByte(MsgTypes.DataReply);
            byteBuffer.appendInt(reqId);
            write(replyTopic, replyMsg, objectByteWriter, charset, session);
        }
    }
}

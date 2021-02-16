package org.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.core.JetlangBuffer;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.web.NioWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NioJetlangSendFiber<T> {

    private final Fiber sendFiber;
    private final Buffer<T> buffer;
    private final List<ChannelState> sessions = new ArrayList<>();

    public NioJetlangSendFiber(Fiber sendFiber, ObjectByteWriter<T> objectByteWriter, Charset charset) {
        this.sendFiber = sendFiber;
        this.buffer = new Buffer<>(objectByteWriter, charset);
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
        public final NioWriter channel;
        private final NioJetlangRemotingClientFactory.Id id;
        private final NioFiber fiber;
        private final Set<String> subscriptions = new HashSet<>();

        public ChannelState(NioWriter channel, NioJetlangRemotingClientFactory.Id id, NioFiber fiber) {
            this.channel = channel;
            this.id = id;
            this.fiber = fiber;
        }

        private void close(NioControls controls) {
            controls.close(channel.getChannel());
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
        });
    }

    private static class Buffer<T> {

        private final ObjectByteWriter<T> objectByteWriter;
        private final Charset charset;
        private final JetlangBuffer byteBuffer;

        public Buffer(ObjectByteWriter<T> objectByteWriter, Charset charset) {
            this.objectByteWriter = objectByteWriter;
            this.charset = charset;
            this.byteBuffer = new JetlangBuffer(1024);
        }

        public void flush(ChannelState session) {
            ByteBuffer toSend = byteBuffer.getBuffer();
            toSend.flip();
            executeFlush(toSend, session);
            toSend.clear();
        }

        private void executeFlush(ByteBuffer toSend, ChannelState session) {
            session.channel.send(toSend);
        }

        public void append(String topic, T object) {
            byteBuffer.appendMsg(topic, object, objectByteWriter, charset);
        }

        public void write(String topic, T msg, ChannelState channel) {
            append(topic, msg);
            flush(channel);
        }
        public void writeReply(int reqId, String replyTopic, T replyMsg, ChannelState session) {
            byteBuffer.appendReply(reqId, replyTopic, replyMsg, objectByteWriter, charset);
            flush(session);
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
            ByteBuffer bb = byteBuffer.getBuffer();
            bb.position(position);
            flush(session);
        }

        public int position() {
            return byteBuffer.position();
        }
    }
}

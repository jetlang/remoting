package org.jetlang.remote.acceptor;

import org.jetlang.channels.Channel;
import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscribable;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.core.Filter;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.remote.core.JetlangBuffer;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.Serializer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RemoteSubscriptions<W> {

    private final Channel<Subscription<W>> subscriptions = new MemoryChannel<>();
    private final Channel<Subscription<W>> unsub = new MemoryChannel<>();
    private final Supplier<ObjectByteWriter<W>> serializer;
    private final Charset topicCharSet;

    public static <T> Subscribable<T> onReadThread(Consumer<T> dispatch) {
        final SynchronousDisposingExecutor sync = new SynchronousDisposingExecutor();
        return new Subscribable<T>() {
            @Override
            public DisposingExecutor getQueue() {
                return sync;
            }

            @Override
            public void onMessage(T message) {
                dispatch.accept(message);
            }
        };
    }

    public RemoteSubscriptions(Serializer<?, W> serializer) {
        this(serializer::getWriter, StandardCharsets.US_ASCII);
    }

    public RemoteSubscriptions(Supplier<ObjectByteWriter<W>> serializer, Charset topicCharSet) {
        this.serializer = serializer;
        this.topicCharSet = topicCharSet;
    }

    void publishSubscription(Subscription<W> subscription) {
        subscriptions.publish(subscription);
    }

    void unsubscribe(Subscription<W> sub) {
        unsub.publish(sub);
    }

    public <T extends W> Subscribers<T> subscribe(DisposingExecutor executor, String topic, Callback<Subscription<T>> onSubscribe, Callback<Object> onUnsubscribe, int initialSendBufferSize) {
        final ArrayList<Subscription<T>> subs = new ArrayList<Subscription<T>>();
        final ObjectByteWriter<T> writer = (ObjectByteWriter<T>) this.serializer.get();
        Callback<Subscription<W>> gen = (msg) -> {
            Subscription<T> cast = (Subscription<T>) msg;
            subs.add(cast);
            onSubscribe.onMessage(cast);
        };
        Filter<Subscription<W>> filterByTopic = (sub) -> sub.topic().equals(topic);
        Disposable newSub = subscriptions.subscribe(new ChannelSubscription<>(executor, gen, filterByTopic));
        Callback<Subscription<W>> genUnSub = (msg) -> {
            subs.remove(msg);
            onUnsubscribe.onMessage(msg.session());
        };
        Disposable unsubClose = unsub.subscribe(new ChannelSubscription<>(executor, genUnSub, filterByTopic));
        Disposable disposable = () -> {
            newSub.dispose();
            unsubClose.dispose();
        };
        return new Subscribers<T>(topic, subs, disposable, writer, topicCharSet, initialSendBufferSize);
    }

    public void onNewSession(JetlangNioSession<?, W> session) {
        final Map<String, Subscription<W>> subs = new HashMap<>();
        session.getSubscriptionRequestChannel().subscribe(onReadThread(req -> {
            Subscription<W> subscription = new Subscription<>(session, req);
            subs.put(subscription.topic(), subscription);
            publishSubscription(subscription);
        }));
        session.getUnsubscribeChannel().subscribe(onReadThread(unsub1 -> {
            final RemoteSubscriptions.Subscription<W> sub = subs.remove(unsub1);
            if (sub != null) {
                unsubscribe(sub);
            }
        }));
        session.getSessionCloseChannel().subscribe(onReadThread(close -> {
            for (RemoteSubscriptions.Subscription<W> sub : subs.values()) {
                unsubscribe(sub);
            }
            subs.clear();
        }));
    }

    public static class Subscription<T> {
        private final JetlangNioSession<?, T> session;
        public final SessionTopic<T> sessionTopic;

        public Subscription(JetlangNioSession<?, T> session, SessionTopic<T> sessionTopic) {
            this.session = session;
            this.sessionTopic = sessionTopic;
        }

        public String topic() {
            return sessionTopic.getTopic();
        }

        public Object session() {
            return session.getSessionId();
        }

        public void send(ByteBuffer sendBuffer) {
            session.getWriter().send(sendBuffer);
        }
    }

    public static class Subscribers<T> implements Disposable {
        private final String topic;
        public final List<Subscription<T>> subscriptions;
        private final Disposable onEnd;
        private final ObjectByteWriter<T> writer;
        private final JetlangBuffer sendBuffer;
        private final int startPosition;

        public Subscribers(String topic, List<Subscription<T>> subscriptions, Disposable onEnd, ObjectByteWriter<T> writer, Charset topicCharSet, int initialSendBufferSize) {
            this.topic = topic;
            this.subscriptions = subscriptions;
            this.onEnd = onEnd;
            this.writer = writer;
            this.sendBuffer = new JetlangBuffer(initialSendBufferSize);
            this.sendBuffer.appendIntAsByte(MsgTypes.Data);
            this.sendBuffer.appendTopic(topic.getBytes(topicCharSet));
            this.startPosition = sendBuffer.getBuffer().position();
        }

        public int publish(T msg) {
            int sz = subscriptions.size();
            if (sz > 0) {
                ByteBuffer beforeWrite = this.sendBuffer.getBuffer();
                beforeWrite.position(startPosition);
                beforeWrite.limit(beforeWrite.capacity());
                this.sendBuffer.writeMsgOnly(topic, msg, writer);
                //get the buffer again b/c it may have been resized
                ByteBuffer buffer = sendBuffer.getBuffer();
                buffer.flip();
                for (int i = 0; i < sz; i++) {
                    subscriptions.get(i).send(buffer);
                    buffer.position(0);
                }
            }
            return sz;
        }

        @Override
        public void dispose() {
            onEnd.dispose();
        }
    }
}

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
import org.jetlang.web.NioWriter;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RemoteSubscriptions<W> {

    private final Channel<SubscriptionTopic<W>> subscriptions = new MemoryChannel<>();
    private final Channel<SubscriptionTopic<W>> unsub = new MemoryChannel<>();
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

    void publishSubscription(SubscriptionTopic<W> subscription) {
        subscriptions.publish(subscription);
    }

    void unsubscribe(SubscriptionTopic<W> sub) {
        unsub.publish(sub);
    }

    public <T extends W> Subscribers<T> subscribe(DisposingExecutor executor, String topic, Callback<Subscription<T>> onSubscribe, Callback<Object> onUnsubscribe, int initialSendBufferSize) {
        final Map<SubscriptionTopic<T>, Subscription<T>> subs = new HashMap<>();
        final ObjectByteWriter<T> writer = (ObjectByteWriter<T>) this.serializer.get();
        final Buffer<T> buffer = new Buffer<>(topic, topicCharSet, initialSendBufferSize, writer);
        Callback<SubscriptionTopic<W>> gen = (msg) -> {
            SubscriptionTopic<T> cast = (SubscriptionTopic<T>) msg;
            Subscription<T> t = new Subscription<>(cast, buffer);
            subs.put(cast, t);
            onSubscribe.onMessage(t);
        };
        Filter<SubscriptionTopic<W>> filterByTopic = (sub) -> sub.topic().equals(topic);
        Disposable newSub = subscriptions.subscribe(new ChannelSubscription<>(executor, gen, filterByTopic));
        Callback<SubscriptionTopic<W>> genUnSub = (msg) -> {
            subs.remove(msg);
            onUnsubscribe.onMessage(msg.session());
        };
        Disposable unsubClose = unsub.subscribe(new ChannelSubscription<>(executor, genUnSub, filterByTopic));
        Disposable disposable = () -> {
            newSub.dispose();
            unsubClose.dispose();
        };
        return new Subscribers<T>(subs, disposable, buffer);
    }

    public void onNewSession(JetlangNioSession<?, W> session) {
        final Map<String, SubscriptionTopic<W>> sessionSubscriptions = new HashMap<>();
        session.getSubscriptionRequestChannel().subscribe(onReadThread(req -> {
            SubscriptionTopic<W> subscription = new SubscriptionTopic<>(session, req);
            sessionSubscriptions.put(subscription.topic, subscription);
            publishSubscription(subscription);
        }));
        session.getUnsubscribeChannel().subscribe(onReadThread(unsub1 -> {
            final RemoteSubscriptions.SubscriptionTopic<W> sub = sessionSubscriptions.remove(unsub1);
            if (sub != null) {
                unsubscribe(sub);
            }
        }));
        session.getSessionCloseChannel().subscribe(onReadThread(close -> {
            for (RemoteSubscriptions.SubscriptionTopic<W> sub : sessionSubscriptions.values()) {
                unsubscribe(sub);
            }
            sessionSubscriptions.clear();
        }));
    }

    public static class SubscriptionTopic<T> {
        private final JetlangNioSession<?, T> session;
        private final String topic;

        public SubscriptionTopic(JetlangNioSession<?, T> session, SessionTopic<T> sessionTopic) {
            this.session = session;
            this.topic = sessionTopic.getTopic();
        }

        public String topic() {
            return topic;
        }

        public Object session() {
            return session.getSessionId();
        }

        public void send(ByteBuffer sendBuffer) {
            session.getWriter().send(sendBuffer);
        }
    }

    public static class Subscription<T> {
        private final SubscriptionTopic<T> topic;
        private final Buffer<T> writer;

        public Subscription(SubscriptionTopic<T> topic, Buffer<T> writer){
            this.topic = topic;
            this.writer = writer;
        }

        public Object session(){
            return topic.session;
        }

        public String topic(){
            return this.topic.topic();
        }

        void send(ByteBuffer buffer) {
            topic.send(buffer);
        }

        public void send(T msg){
            writer.append(msg);
            writer.flushTo(topic);
        }
    }

    private static class Buffer<T> {
        private final JetlangBuffer sendBuffer;
        private final ObjectByteWriter<T> writer;
        private final int startPosition;
        private final String topic;

        public Buffer(String topic, Charset topicCharset, int initialSize, ObjectByteWriter<T> writer){
            this.topic = topic;
            this.sendBuffer = new JetlangBuffer(initialSize);
            this.writer = writer;
            this.sendBuffer.appendIntAsByte(MsgTypes.Data);
            this.sendBuffer.appendTopic(topic.getBytes(topicCharset));
            this.startPosition = sendBuffer.getBuffer().position();
        }

        public void append(T msg) {
            ByteBuffer beforeWrite = this.sendBuffer.getBuffer();
            beforeWrite.position(startPosition);
            beforeWrite.limit(beforeWrite.capacity());
            this.sendBuffer.writeMsgOnly(topic, msg, writer);
        }

        void flushTo(Collection<Subscription<T>> values) {
            //get the buffer again b/c it may have been resized
            ByteBuffer buffer = sendBuffer.getBuffer();
            buffer.flip();
            for (Subscription subscription : values) {
                subscription.send(buffer);
                buffer.position(0);
            }
        }

        void flushTo(SubscriptionTopic<T> writer) {
            ByteBuffer buffer = sendBuffer.getBuffer();
            buffer.flip();
            writer.send(buffer);
        }
    }

    public static class Subscribers<T> implements Disposable {
        private final Map<SubscriptionTopic<T>, Subscription<T>> subscriptions;
        private final Disposable onEnd;
        private final Buffer<T> sendBuffer;

        public Subscribers(Map<SubscriptionTopic<T>, Subscription<T>> subscriptions, Disposable onEnd,
                           Buffer<T> buffer) {
            this.subscriptions = subscriptions;
            this.onEnd = onEnd;
            this.sendBuffer = buffer;
        }

        public int publish(T msg) {
            int sz = subscriptions.size();
            if (sz > 0) {
                this.sendBuffer.append(msg);
                this.sendBuffer.flushTo(subscriptions.values());
            }
            return sz;
        }

        @Override
        public void dispose() {
            onEnd.dispose();
        }
    }
}

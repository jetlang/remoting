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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RemoteSubscriptions<W> {

    private final Channel<Topic<W>> subscriptions = new MemoryChannel<>();
    private final Channel<Topic<W>> unsub = new MemoryChannel<>();
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

    void publishSubscription(Topic<W> subscription) {
        subscriptions.publish(subscription);
    }

    void unsubscribe(Topic<W> sub) {
        unsub.publish(sub);
    }

    public static class FiberSubscriptions<W> {
        private final DisposingExecutor fiber;
        private final ObjectByteWriter<W> writer;
        private final Charset topicCharSet;
        private final Channel<Topic<W>> subscriptions;
        private final Channel<Topic<W>> unsub;
        private final JetlangBuffer buffer;

        public FiberSubscriptions(DisposingExecutor fiber, ObjectByteWriter<W> writer, int initialSendBufferSize, Charset topicCharSet, Channel<Topic<W>> subscriptions, Channel<Topic<W>> unsub) {
            this.fiber = fiber;
            this.writer = writer;
            this.topicCharSet = topicCharSet;
            this.subscriptions = subscriptions;
            this.unsub = unsub;
            this.buffer = new JetlangBuffer(initialSendBufferSize);
        }

        public <T extends W> Subscribers<T> subscribe(String topic, Callback<Subscription<T>> onSubscribe, Callback<Subscription<T>> onUnsubscribe) {
            final Map<Topic<T>, Subscription<T>> subs = new HashMap<>();
            final ObjectByteWriter<T> writer = (ObjectByteWriter<T>) this.writer;
            final Buffer<T> buffer = new Buffer<>(topic, this.topicCharSet, this.buffer, writer);
            Callback<Topic<W>> gen = (msg) -> {
                Topic<T> cast = (Topic<T>) msg;
                Subscription<T> t = new Subscription<>(cast, buffer);
                subs.put(cast, t);
                onSubscribe.onMessage(t);
            };
            Filter<Topic<W>> filterByTopic = (sub) -> sub.topic().equals(topic);
            Disposable newSub = subscriptions.subscribe(new ChannelSubscription<>(fiber, gen, filterByTopic));
            Callback<Topic<W>> genUnSub = (msg) -> {
                Subscription<T> remove = subs.remove(msg);
                if(remove != null) {
                    onUnsubscribe.onMessage(remove);
                }
            };
            Disposable unsubClose = unsub.subscribe(new ChannelSubscription<>(fiber, genUnSub, filterByTopic));
            Disposable disposable = () -> {
                newSub.dispose();
                unsubClose.dispose();
            };
            return new Subscribers<T>(subs, disposable, buffer);
        }
    }

    public FiberSubscriptions<W> onFiber(DisposingExecutor executor, int initialSendBufferSize){
        return new FiberSubscriptions<>(executor, this.serializer.get(), initialSendBufferSize, topicCharSet, subscriptions, unsub);
    }

    public void onNewSession(JetlangNioSession<?, W> session) {
        final Map<String, Topic<W>> sessionSubscriptions = new HashMap<>();
        session.getSubscriptionRequestChannel().subscribe(onReadThread(req -> {
            Topic<W> subscription = new Topic<>(session, req);
            sessionSubscriptions.put(subscription.topic, subscription);
            publishSubscription(subscription);
        }));
        session.getUnsubscribeChannel().subscribe(onReadThread(unsub1 -> {
            final Topic<W> sub = sessionSubscriptions.remove(unsub1);
            if (sub != null) {
                unsubscribe(sub);
            }
        }));
        session.getSessionCloseChannel().subscribe(onReadThread(close -> {
            for (Topic<W> sub : sessionSubscriptions.values()) {
                unsubscribe(sub);
            }
            sessionSubscriptions.clear();
        }));
    }

    public static class Topic<T> {
        private final JetlangNioSession<?, T> session;
        private final String topic;

        public Topic(JetlangNioSession<?, T> session, SessionTopic<T> sessionTopic) {
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

        @Override
        public String toString() {
            return "Topic{" +
                    "session=" + session() +
                    ", topic='" + topic() + '\'' +
                    '}';
        }
    }

    public static class Subscription<T> {
        final Topic<T> topic;
        private final Buffer<T> writer;

        public Subscription(Topic<T> topic, Buffer<T> writer){
            this.topic = topic;
            this.writer = writer;
        }

        public Object session(){
            return topic.session();
        }

        public String topic(){
            return this.topic.topic();
        }

        public void send(T msg){
            writer.append(msg);
            writer.flushTo(topic);
        }

        public void sendRawMsg(ByteBuffer msg){
            writer.appendRawMsg(msg);
            writer.flushTo(topic);
        }
    }

    private static class Buffer<T> {
        private final JetlangBuffer sendBuffer;
        private final ObjectByteWriter<T> writer;
        private final String topic;
        private final byte[] topicBytes;

        public Buffer(String topic, Charset topicCharset, JetlangBuffer jBuf, ObjectByteWriter<T> writer){
            this.topic = topic;
            this.sendBuffer = jBuf;
            this.writer = writer;
            this.topicBytes = topic.getBytes(topicCharset);
        }

        public void append(T msg) {
            ByteBuffer beforeWrite = this.sendBuffer.getBuffer();
            beforeWrite.clear();
            this.sendBuffer.appendMsg(topic, topicBytes, msg, writer);
        }

        public void appendRawMsg(ByteBuffer msg){
            ByteBuffer beforeWrite = this.sendBuffer.getBuffer();
            beforeWrite.clear();
            this.sendBuffer.appendMsg(topicBytes, msg);
        }

        void flushTo(Collection<Subscription<T>> values) {
            ByteBuffer buffer = sendBuffer.getBuffer();
            buffer.flip();
            for (Subscription subscription : values) {
                subscription.topic.send(buffer);
                buffer.position(0);
            }
        }

        void flushTo(Topic<T> writer) {
            ByteBuffer buffer = sendBuffer.getBuffer();
            buffer.flip();
            writer.send(buffer);
        }
    }

    public static class Subscribers<T> implements Disposable {
        private final Map<Topic<T>, Subscription<T>> subscriptions;
        private final Disposable onEnd;
        private final Buffer<T> sendBuffer;

        public Subscribers(Map<Topic<T>, Subscription<T>> subscriptions, Disposable onEnd,
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

        public int publishRawMsg(ByteBuffer msg){
            int sz = subscriptions.size();
            if (sz > 0) {
                this.sendBuffer.appendRawMsg(msg);
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

package org.jetlang.remote.client;

import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscribable;
import org.jetlang.core.Disposable;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.MsgTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class RemoteSubscriptions {

    private final Map<String, RemoteSubscription> remoteSubscriptions = new LinkedHashMap<String, RemoteSubscription>();
    private final Executor sendFiber;
    private final SubscriptionWriter writer;
    private CloseableChannel.Group channelsToClose;

    public <R> void dispatch(String topic, R object) {
        RemoteSubscription channel;
        synchronized (remoteSubscriptions) {
            channel = remoteSubscriptions.get(topic);
        }
        if (channel != null) {
            //noinspection unchecked
            channel.publish(object);
        }
    }

    public <T> Disposable subscribe(String subject, Subscribable<T> callback) {
        synchronized (remoteSubscriptions) {
            final RemoteSubscription<T> remoteSubscription;
            if (remoteSubscriptions.containsKey(subject)) {
                //noinspection unchecked
                remoteSubscription = (RemoteSubscription<T>) remoteSubscriptions.get(subject);
            } else {
                remoteSubscription = new RemoteSubscription<T>(subject);
                remoteSubscriptions.put(subject, remoteSubscription);
            }
            return remoteSubscription.subscribe(callback);
        }
    }

    public void onConnect() {
        synchronized (remoteSubscriptions) {
            for (RemoteSubscription subscription : remoteSubscriptions.values()) {
                subscription.onConnect();
            }
        }
    }

    public interface SubscriptionWriter {
        boolean sendSubscription(String topic);

        void sendUnsubscribe(String topic);
    }

    public RemoteSubscriptions(Executor sendFiber, SubscriptionWriter writer, CloseableChannel.Group channelsToClose) {
        this.sendFiber = sendFiber;
        this.writer = writer;
        this.channelsToClose = channelsToClose;
    }

    private class RemoteSubscription<T> {
        private final CloseableChannel<T> channel = createChannel();
        private final String topic;
        private boolean subscriptionSent = false;

        public RemoteSubscription(String topic) {
            this.topic = topic;
        }

        public Disposable subscribe(Subscribable<T> callback) {
            final Disposable channelDisposable = channel.subscribe(callback);

            sendFiber.execute(() -> {
                if (!subscriptionSent) {
                    subscriptionSent = writer.sendSubscription(topic);
                }
            });
            return () -> {
                channelDisposable.dispose();
                sendFiber.execute(this::unsubscribeIfNecessary);
            };
        }

        private void unsubscribeIfNecessary() {
            synchronized (remoteSubscriptions) {
                if (channel.subscriptionCount() == 0 && !channel.isClosed()) {
                    channel.close();
                    channelsToClose.remove(channel);
                    if (subscriptionSent) {
                        writer.sendUnsubscribe(topic);
                    }
                    remoteSubscriptions.remove(topic);
                }
            }
        }

        public void publish(T object) {
            channel.publish(object);
        }

        public void onConnect() {
            subscriptionSent = writer.sendSubscription(topic);
        }

    }

    private <T> CloseableChannel<T> createChannel() {
        return channelsToClose.add(new MemoryChannel<>());
    }
}
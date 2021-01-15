package org.jetlang.remote.client;

import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.Subscribable;
import org.jetlang.channels.Subscriber;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.remote.core.ReadTimeoutEvent;

import java.util.concurrent.TimeUnit;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 5:59 PM
 */
public interface JetlangClient<R, W> {

    Subscriber<ConnectEvent> getConnectChannel();

    Subscriber<CloseEvent> getCloseChannel();

    Subscriber<ReadTimeoutEvent> getReadTimeoutChannel();

    Subscriber<DeadMessageEvent<W>> getDeadMessageChannel();

    void publish(String topic, W msg);

    <T extends R> Disposable subscribe(String subject, Subscribable<T> callback);

    default <T extends R> Disposable subscribe(String topic, DisposingExecutor clientFiber, Callback<T> cb) {
        return subscribe(topic, new ChannelSubscription<T>(clientFiber, cb));
    }

    default  <T extends R> Disposable subscribeOnReadThread(String topic, Callback<T> msg) {
        return subscribe(topic, new Subscribable<T>() {
            private final SynchronousDisposingExecutor unused = new SynchronousDisposingExecutor();
            @Override
            public void onMessage(T t) {
                msg.onMessage(t);
            }
            @Override
            public DisposingExecutor getQueue() {
                return unused;
            }
        });
    }


    void start();

    LogoutResult close(boolean sendLogoutIfStillConnected);

    <T extends W, C extends R> Disposable request(String reqTopic,
                                                  T req,
                                                  DisposingExecutor executor,
                                                  Callback<C> callback,
                                                  Callback<TimeoutControls> timeoutRunnable,
                                                  int timeout,
                                                  TimeUnit timeUnit);

    void execOnSendThread(Callback<SocketWriter<W>> cb);
}

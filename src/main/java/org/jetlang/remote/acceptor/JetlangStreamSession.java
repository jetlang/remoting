package org.jetlang.remote.acceptor;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.MsgTypes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JetlangStreamSession<R, W> extends JetlangBaseSession<R, W> implements JetlangRemotingProtocol.Handler<R> {

    private final MessageStreamWriter<W> socket;
    private final Fiber sendFiber;
    private final ErrorHandler errorHandler;
    private final Set<String> subscriptions = Collections.synchronizedSet(new HashSet<String>());
    private volatile boolean loggedOut;

    private volatile Runnable hbStopper = new Runnable() {
        @Override
        public void run() {
        }
    };

    public JetlangStreamSession(Object id, MessageStreamWriter<W> socket, Fiber sendFiber, ErrorHandler errorHandler) {
        super(id);
        this.socket = socket;
        this.sendFiber = sendFiber;
        this.errorHandler = errorHandler;
    }

    public void startHeartbeat(int interval, TimeUnit unit) {
        if (interval > 0) {
            Runnable send = new Runnable() {
                @Override
                public void run() {
                    write(MsgTypes.Heartbeat);
                }

                @Override
                public String toString() {
                    return "JetlangStreamSession.writeHeartbeat()";
                }
            };
            final Disposable disposeHb = sendFiber.scheduleWithFixedDelay(send, interval, interval, unit);
            hbStopper = new Runnable() {
                AtomicBoolean stopped = new AtomicBoolean(false);

                @Override
                public void run() {
                    if (stopped.compareAndSet(false, true)) {
                        disposeHb.dispose();
                    }
                }
            };
        }
    }

    @Override
    public void onSubscriptionRequest(String topic) {
        subscriptions.add(topic);
        SubscriptionRequest.publish(new SessionTopic<W>(topic, this));
    }

    @Override
    public void onUnsubscribeRequest(String top) {
        subscriptions.remove(top);
        UnsubscribeRequest.publish(top);
    }

    private void write(final int byteToWrite) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    socket.writeByteAsInt(byteToWrite);
                } catch (IOException e) {
                    handleDisconnect(e);
                }
            }
        };
        sendFiber.execute(r);
    }

    private void handleDisconnect(IOException e) {
        socket.tryClose();
        if (!loggedOut) {
            errorHandler.onException(e);
        }
    }

    @Override
    public void disconnect() {
        socket.tryClose();
    }

    @Override
    public void onLogout() {
        write(MsgTypes.Disconnect);
        Logout.publish(new LogoutEvent());
        loggedOut = true;
        hbStopper.run();
    }

    @Override
    public void onUnknownMessage(int read) {
        errorHandler.onException(new RuntimeException("Unknown message type " + read + " from " + getSessionId()));
    }

    @Override
    public void onHandlerException(Exception failed) {
        errorHandler.onException(failed);
    }

    @Override
    public void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed) {
        errorHandler.onParseFailure(topic, buffer, startingPosition, dataSizeVal, failed);
    }

    @Override
    public void onClientDisconnect(IOException ioException) {
        errorHandler.onClientDisconnect(ioException);
    }

    @Override
    public void publish(final String topic, final W msg) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (subscriptions.contains(topic)) {
                    try {
                        socket.write(topic, msg);
                    } catch (IOException e) {
                        handleDisconnect(e);
                    }
                }
            }

            public String toString() {
                return "JetlangStreamSession.publish(" + topic + ", " + msg + ")";
            }
        };
        sendFiber.execute(r);
    }

    @Override
    public void publish(final byte[] data) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                writeBytesOnSendFiberThread(data);
            }
        };
        sendFiber.execute(r);
    }

    private void writeBytesOnSendFiberThread(byte[] data) {
        try {
            socket.writeBytes(data);
        } catch (IOException e) {
            handleDisconnect(e);
        }
    }

    @Override
    public void reply(final int reqId, final String replyTopic, final W replyMsg) {
        Runnable replyRunner = new Runnable() {
            @Override
            public void run() {
                try {
                    socket.writeReply(reqId, replyTopic, replyMsg);
                } catch (IOException e) {
                    handleDisconnect(e);
                }
            }
        };
        sendFiber.execute(replyRunner);
    }

    @Override
    public void onRequestReply(int reqId, String dataTopicVal, Object readObject) {
        errorHandler.onException(new RuntimeException("Reply is not supported: " + dataTopicVal + " msg: " + readObject));
    }

    @Override
    public void publishIfSubscribed(String topic, final byte[] data) {
        if (subscriptions.contains(topic)) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    writeBytesOnSendFiberThread(data);
                }
            };
            sendFiber.execute(r);
        }
    }
}

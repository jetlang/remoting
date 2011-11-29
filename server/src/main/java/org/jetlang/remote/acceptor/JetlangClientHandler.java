package org.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.core.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JetlangClientHandler implements Acceptor.ClientHandler, ClientPublisher {

    private final SerializerAdapter ser;
    private final NewSessionHandler channels;
    private final Executor exec;
    private final JetlangSessionConfig config;
    private final FiberFactory fiberFactory;
    private final ErrorHandler errorHandler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Collection<ClientTcpSocket> clients = new HashSet<ClientTcpSocket>();
    private final BufferedSerializer globalBuffer;

    private final Fiber globalSendFiber;

    public interface FiberFactory {

        Fiber createGlobalSendFiber();

        Fiber createSendFiber(Socket socket);

        class ThreadFiberFactory implements FiberFactory {

            public Fiber createGlobalSendFiber() {
                return new ThreadFiber();
            }

            public Fiber createSendFiber(Socket socket) {
                return new ThreadFiber();
            }
        }
    }

    public JetlangClientHandler(SerializerFactory fact, NewSessionHandler channels,
                                Executor exec,
                                JetlangSessionConfig config,
                                FiberFactory fiberFactory,
                                ErrorHandler errorHandler){
        this(new SerializerAdapter(fact), channels, exec, config, fiberFactory, errorHandler);
    }

    public JetlangClientHandler(SerializerAdapter ser,
                                NewSessionHandler channels,
                                Executor exec,
                                JetlangSessionConfig config,
                                FiberFactory fiberFactory,
                                ErrorHandler errorHandler) {
        this.ser = ser;
        this.channels = channels;
        this.exec = exec;
        this.config = config;
        this.fiberFactory = fiberFactory;
        this.errorHandler = errorHandler;
        this.globalSendFiber = fiberFactory.createGlobalSendFiber();
        this.globalSendFiber.start();
        this.globalBuffer = ser.createBuffered();
    }

    public void startClient(Socket socket) {
        ClientTcpSocket client = new ClientTcpSocket(new TcpSocket(socket, errorHandler));
        synchronized (clients) {
            if (running.get()) {
                clients.add(client);
            } else {
                stopAndRemove(client);
                return;
            }
        }
        try {
            configureClientSocketAfterAccept(socket);
            Runnable clientReader = createRunnable(client);
            exec.execute(clientReader);
        } catch (IOException e) {
            errorHandler.onException(e);
            stopAndRemove(client);
        }
    }

    public void close() {
        globalSendFiber.dispose();
        synchronized (clients) {
            if (running.compareAndSet(true, false)) {
                for (ClientTcpSocket client : clients) {
                    client.close();
                }
                clients.clear();
            }
        }
    }

    private void stopAndRemove(ClientTcpSocket client) {
        client.close();
        synchronized (clients) {
            clients.remove(client);
        }
    }

    public int clientCount() {
        synchronized (clients) {
            return clients.size();
        }
    }

    /**
     * Publishes asynchronously on a separate global fiber, so be careful of message ordering when using with other fibers/threads.
     *
     * Serialization and enqueueing are done on a separate fiber.
     */
    public void publishToAllSubscribedClients(final String topic, final Object msg) {
        Runnable toSend = new Runnable() {
            public void run() {
                byte[] copy = globalBuffer.createArray(topic, msg);
                enqueueToAllSubscribedClients(topic, copy);
            }
        };
        globalSendFiber.execute(toSend);
    }

    /**
     * Places the serialized bytes into the send q's for all subscribed clients.
     *
     * Subscriptions are made on another thread so it is possible that this will enqueue a message to a client that hasn't been handled in a new session callback.
     */
    public void enqueueToAllSubscribedClients(String topic, byte[] data) {
        synchronized (clients) {
            for (ClientTcpSocket client : clients) {
                client.publishIfSubscribed(topic, data);
            }
        }
    }


    private Runnable createRunnable(final ClientTcpSocket clientTcpSocket) throws IOException {
        final TcpSocket socket = clientTcpSocket.getSocket();
        final Fiber sendFiber = fiberFactory.createSendFiber(socket.getSocket());
        final Serializer serializer = ser.createForSocket(socket);
        final JetlangStreamSession session = new JetlangStreamSession(socket.getRemoteSocketAddress(), new SocketMessageStreamWriter(socket, ser.getCharset(), serializer.getWriter()), sendFiber, errorHandler);
        return new Runnable() {
            public void run() {
                try {
                    Runnable onReadTimeout = new Runnable() {
                        public void run() {
                            session.onReadTimeout(new ReadTimeoutEvent());
                        }
                    };
                    StreamReader input = new StreamReader(socket.getInputStream(), ser.getCharset(), serializer.getReader(), onReadTimeout);
                    clientTcpSocket.setSession(session);
                    channels.onNewSession(JetlangClientHandler.this, session);
                    session.startHeartbeat(config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
                    sendFiber.start();
                    while (readFromStream(input, session)) {

                    }
                } catch (IOException disconnect) {
                    //failed.printStackTrace();
                } catch (Exception clientFailure) {
                    errorHandler.onException(clientFailure);
                } finally {
                    sendFiber.dispose();
                    stopAndRemove(clientTcpSocket);
                    session.onClose(new SessionCloseEvent());
                }
            }
        };
    }

    private void configureClientSocketAfterAccept(Socket socket) throws SocketException {
        socket.setTcpNoDelay(config.getTcpNoDelay());
        if (config.getReceiveBufferSize() > 0)
            socket.setReceiveBufferSize(config.getReceiveBufferSize());
        if (config.getSendBufferSize() > 0)
            socket.setSendBufferSize(config.getSendBufferSize());
        if (config.getReadTimeoutInMs() > 0)
            socket.setSoTimeout(config.getReadTimeoutInMs());
    }

    private boolean readFromStream(StreamReader input, JetlangStreamSession session) throws IOException {
        int read = input.readByteAsInt();
        if (read < 0) {
            return false;
        }
        switch (read) {
            case MsgTypes.Heartbeat:
                session.onHb();
                break;
            case MsgTypes.Subscription:
                int topicSizeInBytes = input.readByteAsInt();
                String topic = input.readString(topicSizeInBytes);
                session.onSubscriptionRequest(topic);
                break;
            case MsgTypes.Unsubscribe:
                int unsubSize = input.readByteAsInt();
                String top = input.readString(unsubSize);
                session.onUnsubscribeRequest(top);
                break;
            case MsgTypes.Disconnect:
                session.write(MsgTypes.Disconnect);
                session.onLogout();
                break;
            case MsgTypes.Data:
                int topicSize = input.readByteAsInt();
                String msgTopic = input.readString(topicSize);
                int msgSize = input.readInt();
                Object msg = input.readObject(msgTopic, msgSize);
                session.onMessage(msgTopic, msg);
                break;
            case MsgTypes.DataRequest:
                int reqId = input.readInt();
                int reqtopicSize = input.readByteAsInt();
                String reqmsgTopic = input.readString(reqtopicSize);
                int reqmsgSize = input.readInt();
                Object reqmsg = input.readObject(reqmsgTopic, reqmsgSize);
                session.onRequest(reqId, reqmsgTopic, reqmsg);
                break;
            default:
                errorHandler.onException(
                        new RuntimeException("Unknown message type " + read + " from " + session.getSessionId()));
                return false;
        }
        return true;
    }

}

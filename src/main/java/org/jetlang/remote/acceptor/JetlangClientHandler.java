package org.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.JetlangRemotingInputStream;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.RawMsgHandler;
import org.jetlang.remote.core.RawMsgHandlerFactory;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.SerializerFactory;
import org.jetlang.remote.core.SocketMessageStreamWriter;
import org.jetlang.remote.core.TcpSocket;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetlang.remote.core.RawMsgHandlerFactory.NULL_RAW_MSG_HANDLER_FACTORY;

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
    private final RawMsgHandlerFactory rawMsgHandlerFactory;

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
                                ErrorHandler errorHandler) {
        this(new SerializerAdapter(fact), channels, exec, config, fiberFactory, errorHandler);
    }

    public JetlangClientHandler(SerializerAdapter ser,
                                NewSessionHandler channels,
                                Executor exec,
                                JetlangSessionConfig config,
                                FiberFactory fiberFactory,
                                ErrorHandler errorHandler) {
       this(ser, channels, exec, config, fiberFactory, errorHandler, NULL_RAW_MSG_HANDLER_FACTORY);
    }

    public JetlangClientHandler(SerializerAdapter ser,
                                NewSessionHandler channels,
                                Executor exec,
                                JetlangSessionConfig config,
                                FiberFactory fiberFactory,
                                ErrorHandler errorHandler,
                                RawMsgHandlerFactory rawMsgHandlerFactory) {
        this.ser = ser;
        this.channels = channels;
        this.exec = exec;
        this.config = config;
        this.fiberFactory = fiberFactory;
        this.errorHandler = errorHandler;
        this.globalSendFiber = fiberFactory.createGlobalSendFiber();
        this.rawMsgHandlerFactory = rawMsgHandlerFactory;
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
     * Subscriptions are made on another thread so it is possible that this will enqueue a message to a client that hasn't been handled in a new session callback.
     */
    public void enqueueToAllSubscribedClients(String topic, byte[] data) {
        synchronized (clients) {
            for (ClientTcpSocket client : clients) {
                client.publishIfSubscribed(topic, data);
            }
        }
    }

    private static class ReadTimeoutHandler implements Runnable {
        private final JetlangStreamSession session;
        public boolean userLoggedOut;

        public ReadTimeoutHandler(JetlangStreamSession session) {
            this.session = session;
        }

        public void run() {
            if (!userLoggedOut) {
                session.onReadTimeout(new ReadTimeoutEvent());
            }
        }
    }


    private Runnable createRunnable(final ClientTcpSocket clientTcpSocket) throws IOException {
        final TcpSocket socket = clientTcpSocket.getSocket();
        final Fiber sendFiber = fiberFactory.createSendFiber(socket.getSocket());
        final Serializer serializer = ser.createForSocket(socket);
        final SocketMessageStreamWriter streamWriter = new SocketMessageStreamWriter(socket, ser.getCharset(), serializer.getWriter());
        final RawMsgHandler rawMsgHandler = rawMsgHandlerFactory.rawMsgHandler();
        final JetlangStreamSession session = new JetlangStreamSession(socket.getRemoteSocketAddress(), streamWriter,
                                                                      sendFiber, errorHandler, rawMsgHandler);
        return new Runnable() {
            public void run() {
                try {
                    ReadTimeoutHandler onReadTimeout = new ReadTimeoutHandler(session);
                    clientTcpSocket.setSession(session);
                    channels.onNewSession(JetlangClientHandler.this, session);
                    session.startHeartbeat(config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
                    sendFiber.start();

                    JetlangRemotingProtocol protocol = new JetlangRemotingProtocol(session, serializer.getReader(), ser.getCharset(), rawMsgHandler.enabled());
                    JetlangRemotingInputStream state = new JetlangRemotingInputStream(socket.getInputStream(), protocol, onReadTimeout);
                    while (state.readFromStream()) {

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
}

package com.jetlang.remote.server;

import com.jetlang.remote.core.*;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JetlangClientHandler implements Acceptor.ClientHandler {

    private final SerializerFactory ser;
    private final JetlangSessionChannels channels;
    private final Executor exec;
    private final JetlangSessionConfig config;
    private final FiberFactory fiberFactory;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final HashSet<Socket> clients = new HashSet<Socket>();
    private final Charset charset = Charset.forName("US-ASCII");

    public static interface FiberFactory {

        Fiber createSendFiber(Socket socket);

        public static class ThreadFiberFactory implements FiberFactory {

            public Fiber createSendFiber(Socket socket) {
                return new ThreadFiber();
            }
        }
    }

    public JetlangClientHandler(SerializerFactory ser,
                                JetlangSessionChannels channels,
                                Executor exec,
                                JetlangSessionConfig config,
                                FiberFactory fiberFactory) {
        this.ser = ser;
        this.channels = channels;
        this.exec = exec;
        this.config = config;
        this.fiberFactory = fiberFactory;
    }

    public void startClient(final Socket socket) {
        synchronized (clients) {
            if (running.get()) {
                clients.add(socket);
            } else {
                close(socket);
                return;
            }
        }
        try {
            Runnable clientReader = createRunnable(socket);
            exec.execute(clientReader);
        } catch (IOException e) {
            close(socket);
        }
    }

    public void close() {
        synchronized (clients) {
            if (running.compareAndSet(true, false)) {
                for (Socket client : clients) {
                    close(client);
                }
            }
        }
    }

    private void close(Socket client) {
        try {
            if (!client.isClosed())
                client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        synchronized (clients) {
            clients.remove(client);
        }
    }

    public int clientCount() {
        synchronized (clients) {
            return clients.size();
        }
    }

    private Runnable createRunnable(final Socket socket) throws IOException {
        final Fiber fiber = fiberFactory.createSendFiber(socket);
        fiber.start();
        final Serializer serializer = ser.createForSocket(socket);
        configureClientSocketAfterAccept(socket);
        final JetlangStreamSession session = new JetlangStreamSession(socket.getInetAddress(), new SocketMessageStreamWriter(socket, charset, serializer.getWriter()), fiber);
        channels.publishNewSession(session);
        session.startHeartbeat(config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
        final Runnable onReadTimeout = new Runnable() {

            public void run() {
                session.ReadTimeout.publish(new ReadTimeoutEvent());
            }
        };
        return new Runnable() {
            public void run() {
                try {
                    final StreamReader input = new StreamReader(socket.getInputStream(), charset, serializer.getReader(), onReadTimeout);

                    while (readFromStream(input, session)) {

                    }
                } catch (Exception failed) {
                    //failed.printStackTrace();
                }
                fiber.dispose();
                close(socket);
                session.SessionClose.publish(new SessionCloseEvent());
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
            default:
                System.err.println("Unknown message type: " + read);
                return false;
        }
        return true;
    }

}

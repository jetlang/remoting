package com.jetlang.remote.server;

import com.jetlang.remote.core.*;
import org.jetlang.fibers.ThreadFiber;

import java.io.IOException;
import java.net.Socket;
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
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final HashSet<Socket> clients = new HashSet<Socket>();
    private final Charset charset = Charset.forName("US-ASCII");

    public JetlangClientHandler(SerializerFactory ser,
                                JetlangSessionChannels channels,
                                Executor exec, JetlangSessionConfig config) {
        this.ser = ser;
        this.channels = channels;
        this.exec = exec;
        this.config = config;
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
        Runnable clientReader = null;
        try {
            clientReader = createRunnable(socket);
        } catch (IOException e) {
            clients.remove(socket);
            close(socket);
        }
        exec.execute(clientReader);
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
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable createRunnable(final Socket socket) throws IOException {
        ThreadFiber fiber = new ThreadFiber();
        fiber.start();
        Serializer serializer = ser.createForSocket(socket);

        final JetlangSession session = new JetlangSession(new SocketMessageStreamWriter(socket, charset, serializer.getWriter()), fiber);
        channels.publishNewSession(session);
        session.startHeartbeat(config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
        return new Runnable() {
            public void run() {
                try {
                    final StreamReader input = new StreamReader(socket.getInputStream(), charset);

                    while (readFromStream(input, session)) {
                    }
                } catch (Exception failed) {
                    failed.printStackTrace();
                }
                channels.publishSessionEnd(session);
            }
        };
    }

    private boolean readFromStream(StreamReader input, JetlangSession session) throws IOException {
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
            case MsgTypes.Disconnect:
                session.write(MsgTypes.Disconnect);
                session.onLogout();
                break;
            default:
                System.err.println("Unknown message type: " + read);
                return false;
        }
        return true;
    }

}

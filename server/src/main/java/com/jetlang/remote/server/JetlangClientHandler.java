package com.jetlang.remote.server;

import com.jetlang.remote.core.MsgTypes;
import com.jetlang.remote.core.Serializer;
import com.jetlang.remote.core.StreamReader;
import org.jetlang.fibers.ThreadFiber;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JetlangClientHandler implements Acceptor.ClientHandler {

    private final Serializer ser;
    private final JetlangSessionChannels channels;
    private final Executor exec;
    private final JetlangSessionConfig config;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final HashSet<Socket> clients = new HashSet<Socket>();

    public JetlangClientHandler(Serializer ser,
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
        Runnable clientReader = createRunnable(socket);
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

    private Runnable createRunnable(final Socket socket) {
        ThreadFiber fiber = new ThreadFiber();
        fiber.start();
        final JetlangSession session = new JetlangSession(socket, fiber);
        channels.publishNewSession(session);
        session.startHeartbeat(config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
        return new Runnable() {
            public void run() {
                try {
                    final StreamReader input = new StreamReader(socket.getInputStream());

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

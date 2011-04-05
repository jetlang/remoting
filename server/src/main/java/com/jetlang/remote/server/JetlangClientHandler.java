package com.jetlang.remote.server;

import com.jetlang.remote.core.Serializer;
import com.jetlang.remote.core.StreamReader;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class JetlangClientHandler implements Acceptor.ClientHandler {

    private final Serializer ser;
    private final JetlangSessionChannels channels;
    private final Executor exec;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final HashSet<Socket> clients = new HashSet<Socket>();

    public JetlangClientHandler(Serializer ser,
                                JetlangSessionChannels channels,
                                Executor exec) {
        this.ser = ser;
        this.channels = channels;
        this.exec = exec;
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
        final JetlangSession session = new JetlangSession(socket);
        channels.publishNewSession(session);
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
            case 1: //subscription
                int topicSizeInBytes = input.readByteAsInt();
                String topic = input.readString(topicSizeInBytes);
                session.onSubscriptionRequest(topic);
                break;
            default:
                System.err.println("Unknown message type: " + read);
        }
        return true;
    }

}

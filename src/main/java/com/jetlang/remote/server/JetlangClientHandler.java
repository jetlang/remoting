package com.jetlang.remote.server;

import com.jetlang.remote.core.Serializer;
import com.jetlang.remote.core.StreamReader;

import java.io.IOException;
import java.net.Socket;

public class JetlangClientHandler implements Acceptor.ClientHandler {

    private final Serializer ser;
    private final JetlangSessionChannels channels;

    public JetlangClientHandler(Serializer ser,
                                JetlangSessionChannels channels) {
        this.ser = ser;
        this.channels = channels;
    }

    public void startClient(final Socket socket) {
        final JetlangSession session = new JetlangSession(socket);
        channels.publishNewSession(session);
        Runnable clientReader = new Runnable() {
            public void run() {
                try {
                    final StreamReader input = new StreamReader(socket.getInputStream());

                    while (readFromStream(input)) {
                    }
                } catch (Exception failed) {
                    failed.printStackTrace();
                }
                channels.publishSessionEnd(session);
            }
        };
        Thread thread = new Thread(clientReader);
        thread.start();
    }

    private boolean readFromStream(StreamReader input) throws IOException {
        int read = input.readByteAsInt();
        if (read < 0) {
            return false;
        }
        switch (read) {
            case 1: //subscription
                int topicSizeInBytes = input.readByteAsInt();
                String topic = input.readString(topicSizeInBytes);
                System.out.println("topic = " + topic);
                break;
            default:
                System.err.println("Unknown message type: " + read);
        }
        return true;
    }

}

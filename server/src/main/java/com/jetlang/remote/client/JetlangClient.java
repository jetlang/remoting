package com.jetlang.remote.client;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 */
public class JetlangClient {

    private final Socket socket;
    private static final Charset charset = Charset.forName("US-ASCII");

    public JetlangClient(Socket socket) {
        this.socket = socket;
    }

    public void subscribe(String subject) {
        try {
            System.out.println("JetlangClient.subscribe");
            byte[] bytes = subject.getBytes(charset);
            socket.getOutputStream().write(1);
            socket.getOutputStream().write(bytes.length);
            socket.getOutputStream().write(bytes);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}

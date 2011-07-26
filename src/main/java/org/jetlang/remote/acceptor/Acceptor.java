package org.jetlang.remote.acceptor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class Acceptor implements Runnable {

    private final ServerSocket port;
    private final ErrorHandler handler;
    private final ClientHandler clientHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public interface ErrorHandler {

        void acceptError(IOException e, AtomicBoolean running);

        void closeError(IOException e);

        @SuppressWarnings({"CallToPrintStackTrace"})
        class SysOut implements ErrorHandler {
            public void acceptError(IOException e, AtomicBoolean running) {
                if (running.get()) {
                    e.printStackTrace();
                }
            }

            public void closeError(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface ClientHandler {

        void startClient(Socket socket);

        void close();
    }

    public Acceptor(ServerSocket port, ErrorHandler handler, ClientHandler clientHandler) {
        this.port = port;
        this.handler = handler;
        this.clientHandler = clientHandler;
    }

    public void run() {
        running.set(true);
        while (running.get()) {
            try {
                Socket socket = port.accept();
                clientHandler.startClient(socket);
            } catch (IOException e) {
                handler.acceptError(e, running);
            }
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                port.close();
                clientHandler.close();
            } catch (IOException e) {
                handler.closeError(e);
            }
        }
    }
}

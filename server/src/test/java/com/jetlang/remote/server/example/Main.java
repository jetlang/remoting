package com.jetlang.remote.server.example;

import com.jetlang.remote.client.JetlangClient;
import com.jetlang.remote.server.Acceptor;
import com.jetlang.remote.server.JetlangClientHandler;
import com.jetlang.remote.server.JetlangSession;
import com.jetlang.remote.server.JetlangSessionChannels;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        JetlangSessionChannels sessions = new JetlangSessionChannels();
        Callback<JetlangSession> onOpen = new Callback<JetlangSession>() {

            public void onMessage(JetlangSession jetlangSession) {
                System.out.println("Session Open: " + jetlangSession);
            }
        };
        Callback<JetlangSession> onClose = new Callback<JetlangSession>() {

            public void onMessage(JetlangSession jetlangSession) {
                System.out.println("Session Close: " + jetlangSession);
            }
        };

        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), onOpen);
        sessions.SessionClose.subscribe(new SynchronousDisposingExecutor(), onClose);

        ExecutorService service = Executors.newCachedThreadPool();
        JetlangClientHandler handler = new JetlangClientHandler(null, sessions, service);

        Acceptor acceptor = new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);

        Thread runner = new Thread(acceptor);
        runner.start();

        Socket socket = new Socket("localhost", 8081);
        JetlangClient client = new JetlangClient(socket);
        client.subscribe("newtopic");
        Thread.sleep(1000);
        acceptor.stop();
        System.out.println("Stopped");

    }
}

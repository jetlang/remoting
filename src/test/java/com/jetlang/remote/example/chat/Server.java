package com.jetlang.remote.example.chat;

import com.jetlang.remote.acceptor.*;
import com.jetlang.remote.core.ByteArraySerializer;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static void main(String[] args) throws IOException {
        int port = 8081;
        if (args.length == 1)
            port = Integer.parseInt(args[0]);

        ExecutorService service = Executors.newCachedThreadPool();
        JetlangSessionConfig sessionConfig = new JetlangSessionConfig();

        NewSessionHandler sessions = new NewSessionHandler() {
            public void onNewSession(final ClientPublisher pub, JetlangSession session) {
                System.out.println("Connect:" + session.getSessionId());
                Callback<SessionMessage<?>> onMsg = new Callback<SessionMessage<?>>() {

                    public void onMessage(SessionMessage sessionMessage) {
                        pub.publishToAllSubscribedClients(sessionMessage.getTopic(), sessionMessage.getMessage());
                    }
                };
                session.getSessionMessageChannel().subscribe(new SynchronousDisposingExecutor(), onMsg);
                session.getSessionCloseChannel().subscribe(new SynchronousDisposingExecutor(),
                        Client.<SessionCloseEvent>print("Close: " + session.getSessionId()));
            }
        };

        JetlangClientHandler handler = new JetlangClientHandler(new ByteArraySerializer.Factory(), sessions,
                service, sessionConfig, new JetlangClientHandler.FiberFactory.ThreadFiberFactory(),
                new JetlangClientHandler.ClientErrorHandler.SysOutClientErrorHandler());
        Acceptor acceptor = new Acceptor(
                new ServerSocket(port),
                new Acceptor.ErrorHandler.SysOut(),
                handler);
        Thread thread = new Thread(acceptor);
        thread.start();

    }
}

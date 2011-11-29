package org.jetlang.remote.example.chat;

import org.jetlang.core.Callback;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.acceptor.*;
import org.jetlang.remote.core.ByteArraySerializer;
import org.jetlang.remote.core.ErrorHandler;

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

        NewFiberSessionHandler sessions = new NewFiberSessionHandler() {
            public void onNewSession(final ClientPublisher pub, JetlangSession session, Fiber fiber) {
                System.out.println("Connect:" + session.getSessionId());
                Callback<SessionMessage<?>> onMsg = new Callback<SessionMessage<?>>() {
                    public void onMessage(SessionMessage sessionMessage) {
                        pub.publishToAllSubscribedClients(sessionMessage.getTopic(), sessionMessage.getMessage());
                    }
                };
                session.getSessionMessageChannel().subscribe(fiber, onMsg);
                session.getSessionCloseChannel().subscribe(fiber,
                        Client.<SessionCloseEvent>print("Close: " + session.getSessionId()));
            }
        };

        final Fiber fiber = new ThreadFiber();
        ByteArraySerializer.Factory factory = new ByteArraySerializer.Factory();
        SerializerAdapter adapter = new SerializerAdapter(factory);
        FiberForAllSessions sessionHandler = new FiberForAllSessions(sessions, fiber, adapter.createBuffered());
        JetlangClientHandler handler = new JetlangClientHandler(factory, sessionHandler,
                service, sessionConfig, new JetlangClientHandler.FiberFactory.ThreadFiberFactory(),
                new ErrorHandler.SysOut());
        final Acceptor acceptor = new Acceptor(
                new ServerSocket(port),
                new Acceptor.ErrorHandler.SysOut(),
                handler);

        Thread thread = new Thread(acceptor);
        thread.start();
        fiber.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                fiber.dispose();
                acceptor.stop();
            }
        });
    }
}

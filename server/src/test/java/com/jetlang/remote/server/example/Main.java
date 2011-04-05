package com.jetlang.remote.server.example;

import com.jetlang.remote.client.JetlangClient;
import com.jetlang.remote.client.SocketConnector;
import com.jetlang.remote.server.Acceptor;
import com.jetlang.remote.server.JetlangClientHandler;
import com.jetlang.remote.server.JetlangSession;
import com.jetlang.remote.server.JetlangSessionChannels;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        JetlangSessionChannels sessions = new JetlangSessionChannels();

        EventAssert serverSessionOpen = EventAssert.expect(1, sessions.SessionOpen);
        final EventAssert subscriptionReceived = new EventAssert(1);
        final EventAssert logoutEvent = new EventAssert(1);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>(){
            public void onMessage(JetlangSession message) {
                subscriptionReceived.subscribe(message.SubscriptionRequest);
                logoutEvent.subscribe(message.Logout);
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);
        EventAssert serverSessionClose = EventAssert.expect(1, sessions.SessionClose);

        ExecutorService service = Executors.newCachedThreadPool();
        JetlangClientHandler handler = new JetlangClientHandler(null, sessions, service);

        Acceptor acceptor = new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);

        Thread runner = new Thread(acceptor);
        runner.start();

        SocketConnector conn = new SocketConnector() {
            public Socket connect() throws IOException {
                return new Socket("localhost", 8081);
            }
        };

        JetlangClient client = new JetlangClient(conn, new ThreadFiber());

        EventAssert clientConnect = EventAssert.expect(1, client.Connected);
        EventAssert clientDisconnect = EventAssert.expect(1, client.Disconnected);
        EventAssert clientClose = EventAssert.expect(1, client.Closed);

        Callback<String> newTopicCb = new Callback<String>(){
            public void onMessage(String message) {
                System.out.println("message = " + message);
            }
        };
        ThreadFiber clientFiber = new ThreadFiber();
        clientFiber.start();

        client.subscribe("newtopic", clientFiber, newTopicCb);
        client.start();

        serverSessionOpen.assertEvent();
        subscriptionReceived.assertEvent();
        assertEquals("newtopic", subscriptionReceived.received.take());
        clientConnect.assertEvent();

        CountDownLatch closeLatch = client.close(true);

        assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
        logoutEvent.assertEvent();
        serverSessionClose.assertEvent();
        clientDisconnect.assertEvent();
        clientClose.assertEvent();
        acceptor.stop();
        System.out.println("Stopped");
        service.shutdownNow();
    }
}

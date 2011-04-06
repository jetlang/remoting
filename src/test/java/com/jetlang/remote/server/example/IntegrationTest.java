package com.jetlang.remote.server.example;

import com.jetlang.remote.client.JetlangClient;
import com.jetlang.remote.client.JetlangClientConfig;
import com.jetlang.remote.client.ReadTimeoutEvent;
import com.jetlang.remote.client.SocketConnector;
import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.server.*;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrationTest {
    JetlangSessionChannels sessions = new JetlangSessionChannels();
    ExecutorService service = Executors.newCachedThreadPool();
    JetlangSessionConfig sessionConfig = new JetlangSessionConfig();
    JetlangClientHandler handler = new JetlangClientHandler(null, sessions, service, sessionConfig);
    SocketConnector conn = new SocketConnector() {
        public Socket connect() throws IOException {
            Socket socket = new Socket("localhost", 8081);
            socket.setSoTimeout(config.getHeartbeatIntervalInMs() * 2);
            return socket;
        }
    };
    JetlangClientConfig config = new JetlangClientConfig();

    @After
    public void shutdown() {
        service.shutdownNow();
    }

    @Test
    public void heartbeat() throws IOException {
        final EventAssert<HeartbeatEvent> hb = new EventAssert<HeartbeatEvent>(3);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>() {
            public void onMessage(JetlangSession message) {
                hb.subscribe(message.Heartbeat);
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);
        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();
        EventAssert<ReadTimeoutEvent> timeout = EventAssert.expect(0, client.ReadTimeout);
        client.start();
        hb.assertEvent();
        client.close(true);
        acceptor.stop();
        timeout.assertEvent();
    }

    @Test
    public void regression() throws IOException, InterruptedException {
        EventAssert serverSessionOpen = EventAssert.expect(1, sessions.SessionOpen);
        final EventAssert<String> subscriptionReceived = new EventAssert<String>(1);
        final EventAssert<LogoutEvent> logoutEvent = new EventAssert<LogoutEvent>(1);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>() {
            public void onMessage(JetlangSession message) {
                subscriptionReceived.subscribe(message.SubscriptionRequest);
                logoutEvent.subscribe(message.Logout);
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);
        EventAssert serverSessionClose = EventAssert.expect(1, sessions.SessionClose);

        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();

        EventAssert clientConnect = EventAssert.expect(1, client.Connected);
        EventAssert clientDisconnect = EventAssert.expect(1, client.Disconnected);
        EventAssert clientClose = EventAssert.expect(1, client.Closed);

        Callback<String> newTopicCb = new Callback<String>() {
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

    private JetlangClient createClient() {
        return new JetlangClient(conn, new ThreadFiber(), config);
    }

    private Acceptor createAcceptor() throws IOException {
        return new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);
    }
}

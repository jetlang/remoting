package com.jetlang.remote.server.example;

import com.jetlang.remote.client.*;
import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.JavaSerializer;
import com.jetlang.remote.core.ReadTimeoutEvent;
import com.jetlang.remote.server.*;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
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
    JetlangClientHandler handler = new JetlangClientHandler(new JavaSerializer.Factory(), sessions, service, sessionConfig);
    JetlangClientConfig clientConfig = new JetlangClientConfig();

    SocketConnector conn = new SocketConnector("localhost", 8081, clientConfig);

    @After
    public void shutdown() {
        service.shutdownNow();
    }

    @Test
    public void heartbeat() throws IOException {
        final EventAssert<HeartbeatEvent> hb = new EventAssert<HeartbeatEvent>(3);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>() {
            public void onMessage(JetlangSession message) {
                hb.subscribe(message.getHeartbeatChannel());
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);
        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();
        EventAssert<ReadTimeoutEvent> timeout = EventAssert.expect(0, client.getReadTimeoutChannel());
        client.start();
        hb.assertEvent();
        client.close(true);
        acceptor.stop();
        timeout.assertEvent();
    }

    @Test
    public void serverHeartbeatTimeout() throws IOException {
        final EventAssert<ReadTimeoutEvent> serverSessionTimeout = new EventAssert<ReadTimeoutEvent>(1);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>() {
            public void onMessage(JetlangSession session) {
                serverSessionTimeout.subscribe(session.getReadTimeoutChannel());
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);

        //short read timeout.
        sessionConfig.setReadTimeoutInMs(10);
        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        //disable heartbeats.
        clientConfig.setHeartbeatIntervalInMs(0);
        JetlangClient client = createClient();
        client.start();
        serverSessionTimeout.assertEvent();
        client.close(true);
        acceptor.stop();
    }

    @Test
    public void regression() throws IOException, InterruptedException {
        EventAssert serverSessionOpen = EventAssert.expect(1, sessions.SessionOpen);
        final EventAssert<SessionTopic> subscriptionReceived = new EventAssert<SessionTopic>(1);
        Callback<SessionTopic> onTopic = new Callback<SessionTopic>() {
            public void onMessage(SessionTopic message) {
                message.publish("mymsg");
            }
        };
        subscriptionReceived.onMessage(onTopic);
        final EventAssert<LogoutEvent> logoutEvent = new EventAssert<LogoutEvent>(1);
        final EventAssert<SessionMessage<?>> serverMessageReceive = new EventAssert<SessionMessage<?>>(1);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>() {
            public void onMessage(JetlangSession message) {
                subscriptionReceived.subscribe(message.getSubscriptionRequestChannel());
                logoutEvent.subscribe(message.getLogoutChannel());
                serverMessageReceive.subscribe(message.getSessionMessageChannel());
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);
        EventAssert<JetlangSession> serverSessionClose = EventAssert.expect(1, sessions.SessionClose);

        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();

        EventAssert<ConnectEvent> clientConnect = EventAssert.expect(1, client.getConnectChannel());
        EventAssert<DisconnectEvent> clientDisconnect = EventAssert.expect(1, client.getDisconnectChannel());
        EventAssert<CloseEvent> clientClose = EventAssert.expect(1, client.getCloseChannel());

        ThreadFiber clientFiber = new ThreadFiber();
        clientFiber.start();

        EventAssert<String> clientMsgReceive = new EventAssert<String>(1);
        client.subscribe("newtopic", clientMsgReceive.asSubscribable());
        client.start();

        serverSessionOpen.assertEvent();
        subscriptionReceived.assertEvent();
        assertEquals("newtopic", subscriptionReceived.takeFromReceived().getTopic());
        clientConnect.assertEvent();
        clientMsgReceive.assertEvent();
        client.publish("toServer", "myclientmessage");
        serverMessageReceive.assertEvent();
        SessionMessage<?> sessionMessage = serverMessageReceive.takeFromReceived();
        assertEquals("toServer", sessionMessage.getTopic());
        assertEquals("myclientmessage", sessionMessage.getMessage());

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
        return new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer());
    }

    private Acceptor createAcceptor() throws IOException {
        return new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);
    }
}

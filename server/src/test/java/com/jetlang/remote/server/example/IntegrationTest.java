package com.jetlang.remote.server.example;

import com.jetlang.remote.client.*;
import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.JavaSerializer;
import com.jetlang.remote.core.ReadTimeoutEvent;
import com.jetlang.remote.server.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
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
    JetlangClientHandler handler = new JetlangClientHandler(new JavaSerializer.Factory(), sessions,
            service, sessionConfig, new JetlangClientHandler.FiberFactory.ThreadFiberFactory(),
            new JetlangClientHandler.ClientErrorHandler.SysOutClientErrorHandler());
    JetlangClientConfig clientConfig = new JetlangClientConfig();

    SocketConnector conn = new SocketConnector("localhost", 8081);

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
    public void disconnect() throws IOException {
        final EventAssert<SessionCloseEvent> closeEvent = new EventAssert<SessionCloseEvent>(1);

        Callback<JetlangSession> sessionCallback = new Callback<JetlangSession>() {
            public void onMessage(JetlangSession session) {
                closeEvent.subscribe(session.getSessionCloseChannel());
                //immediate forced disconnect.
                session.disconnect();
            }
        };
        sessions.SessionOpen.subscribe(new SynchronousDisposingExecutor(), sessionCallback);

        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        //don't allow reconnects
        clientConfig.setReconnectDelayInMs(-1);
        JetlangClient client = createClient();
        client.start();
        closeEvent.assertEvent();
        acceptor.stop();
    }

    @Test
    public void globalPublishToTwoClients() throws IOException {
        final EventAssert<JetlangSession> openEvent = EventAssert.expect(2, sessions.SessionOpen);

        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        EventAssert<Object> msgReceived = new EventAssert<Object>(2);
        JetlangClient client = createClient();
        client.subscribe("topic", msgReceived.asSubscribable());
        client.start();

        JetlangClient client2 = createClient();
        client2.subscribe("topic", msgReceived.asSubscribable());
        client2.start();

        openEvent.assertEvent();

        handler.publishToAllSubscribedClients("topic", "mymsg");

        msgReceived.assertEvent();

        acceptor.stop();
    }

    @Test
    public void requestReplyTimeout() throws IOException {

        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        EventAssert<TimeoutControls> timeoutEvent = new EventAssert<TimeoutControls>(1);
        JetlangClient client = createClient();
        client.start();


        client.request("reqTopic",
                "requestObject",
                new SynchronousDisposingExecutor(), //target fiber
                EventAssert.<Object>callbackNever(), //on reploy
                timeoutEvent.createCallback(),
                10, TimeUnit.MILLISECONDS); //timeout

        timeoutEvent.assertEvent();

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
        final EventAssert<String> unsubscribeReceive = new EventAssert<String>(1);
        final EventAssert<SessionCloseEvent> serverSessionClose = new EventAssert<SessionCloseEvent>(1);

        NewSessionHandler handlerFactory = new NewSessionHandler() {
            public void onNewSession(JetlangSession session, Fiber fiber) {
                subscriptionReceived.subscribe(session.getSubscriptionRequestChannel(), fiber);
                logoutEvent.subscribe(session.getLogoutChannel(), fiber);
                serverMessageReceive.subscribe(session.getSessionMessageChannel(), fiber);
                unsubscribeReceive.subscribe(session.getUnsubscribeChannel(), fiber);
                serverSessionClose.subscribe(session.getSessionCloseChannel(), fiber);
                assertEquals(session.getSessionId(), session.getSessionId());
            }
        };
        FiberPerSession fiberPer = new FiberPerSession(sessions, handlerFactory, new FiberPerSession.FiberFactory.ThreadFiberFactory());

        Acceptor acceptor = createAcceptor();

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();

        EventAssert<ConnectEvent> clientConnect = EventAssert.expect(1, client.getConnectChannel());
        EventAssert<DisconnectEvent> clientDisconnect = EventAssert.expect(1, client.getDisconnectChannel());
        EventAssert<CloseEvent> clientClose = EventAssert.expect(1, client.getCloseChannel());

        EventAssert<String> clientMsgReceive = new EventAssert<String>(1);
        Disposable unsubscribe = client.subscribe("newtopic", clientMsgReceive.asSubscribable());
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
        unsubscribe.dispose();
        unsubscribeReceive.assertEvent();
        assertEquals("newtopic", unsubscribeReceive.takeFromReceived());

        CountDownLatch closeLatch = client.close(true);

        assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
        logoutEvent.assertEvent();
        serverSessionClose.assertEvent();
        clientDisconnect.assertEvent();
        clientClose.assertEvent();
        assertEquals(0, handler.clientCount());
        acceptor.stop();
        service.shutdownNow();
    }

    private JetlangClient createClient() {
        return new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer(), new JetlangTcpClient.ErrorHandler.SysOut());
    }

    private Acceptor createAcceptor() throws IOException {
        return new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);
    }
}

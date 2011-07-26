package com.jetlang.remote;

import com.jetlang.remote.acceptor.*;
import com.jetlang.remote.client.*;
import com.jetlang.remote.core.ErrorHandler;
import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.JavaSerializer;
import com.jetlang.remote.core.ReadTimeoutEvent;
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
    ExecutorService service = Executors.newCachedThreadPool();
    JetlangSessionConfig sessionConfig = new JetlangSessionConfig();
    JetlangClientConfig clientConfig = new JetlangClientConfig();

    SocketConnector conn = new SocketConnector("localhost", 8081);
    JetlangClientHandler handler;

    @After
    public void shutdown() {
        service.shutdownNow();
    }

    @Test
    public void heartbeat() throws IOException {
        final EventAssert<HeartbeatEvent> hb = new EventAssert<HeartbeatEvent>(3);

        NewSessionHandler sessionCallback = new NewSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession message) {
                hb.subscribe(message.getHeartbeatChannel());
            }
        };
        Acceptor acceptor = createAcceptor(sessionCallback);

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();
        EventAssert<ReadTimeoutEvent> timeout = EventAssert.expect(0, client.getReadTimeoutChannel());
        client.start();
        hb.assertEvent();
        close(client);
        acceptor.stop();
        timeout.assertEvent();
    }

    @Test
    public void serverHeartbeatTimeout() throws IOException {
        final EventAssert<ReadTimeoutEvent> serverSessionTimeout = new EventAssert<ReadTimeoutEvent>(1);

        NewSessionHandler sessionCallback = new NewSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession session) {
                serverSessionTimeout.subscribe(session.getReadTimeoutChannel());
            }
        };
        //short read timeout.
        sessionConfig.setReadTimeoutInMs(10);
        Acceptor acceptor = createAcceptor(sessionCallback);

        Thread runner = new Thread(acceptor);
        runner.start();

        //disable heartbeats.
        clientConfig.setHeartbeatIntervalInMs(0);
        JetlangClient client = createClient();
        client.start();
        serverSessionTimeout.assertEvent();
        close(client);
        acceptor.stop();
    }

    @Test
    public void disconnect() throws IOException, InterruptedException {
        final EventAssert<SessionCloseEvent> closeEvent = new EventAssert<SessionCloseEvent>(1);

        NewSessionHandler sessionCallback = new NewSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession session) {
                closeEvent.subscribe(session.getSessionCloseChannel());
                //immediate forced disconnect.
                session.disconnect();
            }
        };

        Acceptor acceptor = createAcceptor(sessionCallback);

        Thread runner = new Thread(acceptor);
        runner.start();

        //don't allow reconnects
        clientConfig.setReconnectDelayInMs(-1);
        JetlangClient client = createClient();
        EventAssert<CloseEvent> clientClose = new EventAssert<CloseEvent>(1);
        clientClose.subscribe(client.getCloseChannel());
        client.start();
        closeEvent.assertEvent();
        acceptor.stop();
        clientClose.assertEvent();
        Class closeClzz = clientClose.received.take().getClass();
        //will receive a read or write exception when the acceptor closes
        assertTrue(closeClzz.toString(), CloseEvent.IOExceptionEvent.class.isAssignableFrom(closeClzz));
    }

    @Test
    public void globalPublishToTwoClients() throws IOException, InterruptedException {
        final EventAssert<JetlangSession> openEvent = new EventAssert<JetlangSession>(2);
        final EventAssert<SessionTopic> subscriptions = EventAssert.create(2);
        NewSessionHandler sessionCallback = new NewSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession session) {
                subscriptions.subscribe(session.getSubscriptionRequestChannel());
                openEvent.receiveMessage(session);
            }
        };

        Acceptor acceptor = createAcceptor(sessionCallback);

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
        subscriptions.assertEvent();

        handler.publishToAllSubscribedClients("topic", "mymsg");

        msgReceived.assertEvent();
        close(client);
        close(client2);
        acceptor.stop();
    }

    private void close(JetlangClient client) {
        try {
            assertTrue(client.close(true).await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void requestReplyTimeout() throws IOException {

        NewSessionHandler sessionCallback = new NewSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession session) {
            }
        };
        Acceptor acceptor = createAcceptor(sessionCallback);

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
        close(client);
        acceptor.stop();
    }

    @Test
    public void requestReply() throws IOException {

        NewSessionHandler sessionCallback = new NewSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession jetlangSession) {
                Callback<SessionRequest> onRequest = new Callback<SessionRequest>() {

                    public void onMessage(SessionRequest sessionRequest) {
                        assertEquals(sessionRequest.getTopic(), "reqTopic");
                        assertEquals(sessionRequest.getRequest(), "requestObject");
                        sessionRequest.reply("replyMsg");
                    }
                };
                jetlangSession.getSessionRequestChannel().subscribe(new SynchronousDisposingExecutor(), onRequest);
            }
        };

        Acceptor acceptor = createAcceptor(sessionCallback);

        Thread runner = new Thread(acceptor);
        runner.start();

        EventAssert<String> reply = new EventAssert<String>(1);
        JetlangClient client = createClient();
        client.start();

        CallbackList<TimeoutControls> timeout = CallbackList.create();

        client.request("reqTopic",
                "requestObject",
                new SynchronousDisposingExecutor(), //target fiber
                reply.createCallback(), //on reply
                timeout, //on timeout
                1000, TimeUnit.MILLISECONDS); //timeout

        reply.assertEvent();
        assertEquals("replyMsg", reply.takeFromReceived());
        assertEquals(0, timeout.received.size());
        close(client);
        acceptor.stop();
    }


    @Test
    public void regression() throws IOException, InterruptedException {
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

        NewFiberSessionHandler handlerFactory = new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangSession session, Fiber fiber) {
                subscriptionReceived.subscribe(session.getSubscriptionRequestChannel(), fiber);
                logoutEvent.subscribe(session.getLogoutChannel(), fiber);
                serverMessageReceive.subscribe(session.getSessionMessageChannel(), fiber);
                unsubscribeReceive.subscribe(session.getUnsubscribeChannel(), fiber);
                serverSessionClose.subscribe(session.getSessionCloseChannel(), fiber);
                assertEquals(session.getSessionId(), session.getSessionId());
            }
        };
        FiberPerSession fiberPer = new FiberPerSession(handlerFactory, new FiberPerSession.FiberFactory.ThreadFiberFactory());

        Acceptor acceptor = createAcceptor(fiberPer);

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();

        EventAssert<ConnectEvent> clientConnect = EventAssert.expect(1, client.getConnectChannel());
        EventAssert<CloseEvent> clientClose = EventAssert.expect(1, client.getCloseChannel());

        EventAssert<String> clientMsgReceive = new EventAssert<String>(1);
        Disposable unsubscribe = client.subscribe("newtopic", clientMsgReceive.asSubscribable());
        client.start();

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
        clientClose.assertEvent();
        assertEquals(CloseEvent.GracefulDisconnect.class, clientClose.received.take().getClass());
        assertEquals(0, handler.clientCount());
        acceptor.stop();
        service.shutdownNow();
    }

    private JetlangClient createClient() {
        return new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer(), new ErrorHandler.SysOut());
    }

    private Acceptor createAcceptor(NewSessionHandler newSession) throws IOException {
        handler = new JetlangClientHandler(new JavaSerializer.Factory(), newSession,
                service, sessionConfig, new JetlangClientHandler.FiberFactory.ThreadFiberFactory(),
                new ErrorHandler.SysOut());
        return new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);
    }
}

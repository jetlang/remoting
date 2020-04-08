package org.jetlang.remote;

import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.acceptor.Acceptor;
import org.jetlang.remote.acceptor.ClientPublisher;
import org.jetlang.remote.acceptor.JetlangClientHandler;
import org.jetlang.remote.acceptor.JetlangFiberSession;
import org.jetlang.remote.acceptor.JetlangSession;
import org.jetlang.remote.acceptor.JetlangSessionConfig;
import org.jetlang.remote.acceptor.LogoutEvent;
import org.jetlang.remote.acceptor.NewFiberSessionHandler;
import org.jetlang.remote.acceptor.NewSessionHandler;
import org.jetlang.remote.acceptor.SerializerAdapter;
import org.jetlang.remote.acceptor.SessionCloseEvent;
import org.jetlang.remote.acceptor.SessionMessage;
import org.jetlang.remote.acceptor.SessionRequest;
import org.jetlang.remote.acceptor.SessionTopic;
import org.jetlang.remote.client.CloseEvent;
import org.jetlang.remote.client.ConnectEvent;
import org.jetlang.remote.client.JetlangClient;
import org.jetlang.remote.client.JetlangClientConfig;
import org.jetlang.remote.client.JetlangTcpClient;
import org.jetlang.remote.client.LogoutResult;
import org.jetlang.remote.client.SocketConnector;
import org.jetlang.remote.client.TimeoutControls;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.JavaSerializer;
import org.jetlang.remote.core.RawMsg;
import org.jetlang.remote.core.RawMsgHandler;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.web.NioReader;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class IntegrationBase {

    ExecutorService service = Executors.newCachedThreadPool();
    JetlangSessionConfig sessionConfig = new JetlangSessionConfig();
    JetlangClientConfig clientConfig = new JetlangClientConfig();
    JavaSerializer.Factory serializerFactory = new JavaSerializer.Factory();
    SerializerAdapter serAdapter = new SerializerAdapter(serializerFactory);


    SocketConnector conn = new SocketConnector("localhost", 8081);
    JetlangClientHandler handler;

    @After
    public void shutdown() {
        service.shutdownNow();
    }

    @Test
    public void heartbeat() throws IOException {
        final EventAssert<HeartbeatEvent> hb = new EventAssert<HeartbeatEvent>(3);

        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession message) {
                hb.subscribe(message.getHeartbeatChannel());
            }
        });
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

        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                serverSessionTimeout.subscribe(session.getReadTimeoutChannel());
            }
        });
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

    protected abstract NewSessionHandler wrap(NewFiberSessionHandler newFiberSessionHandler);

    @Test
    public void disconnect() throws IOException, InterruptedException {
        final EventAssert<SessionCloseEvent> closeEvent = new EventAssert<SessionCloseEvent>(1);

        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                closeEvent.subscribe(session.getSessionCloseChannel());
                //immediate forced disconnect.
                session.disconnect();
            }
        });

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
        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptions.subscribe(session.getSubscriptionRequestChannel());
                openEvent.receiveMessage(session);
            }
        });

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

    @Test
    public void shouldAllowTwoSubscriptionsToTheSameTopicOnAClient() throws IOException, InterruptedException {
        final EventAssert<SessionTopic> subscriptions = EventAssert.create(1);
        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptions.subscribe(session.getSubscriptionRequestChannel());
            }
        });

        Acceptor acceptor = createAcceptor(sessionCallback);

        Thread runner = new Thread(acceptor);
        runner.start();

        EventAssert<Object> msgReceived = new EventAssert<Object>(2);
        JetlangClient client = createClient();
        client.subscribe("topic", msgReceived.asSubscribable());
        client.subscribe("topic", msgReceived.asSubscribable());
        client.start();

        subscriptions.assertEvent();

        handler.publishToAllSubscribedClients("topic", "mymsg");

        msgReceived.assertEvent();
        close(client);
        acceptor.stop();
    }

    @Test
    public void shouldUnsubscribeFromRemoteOnlyAfterAllClientUnsubscribed() throws IOException {
        final EventAssert<SessionTopic> subscriptionReceived = new EventAssert<SessionTopic>(1);
        final EventAssert<String> unsubscribeReceive = new EventAssert<String>(1);

        NewFiberSessionHandler handlerFactory = new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptionReceived.subscribe(session.getSubscriptionRequestChannel(), session.getFiber());
                unsubscribeReceive.subscribe(session.getUnsubscribeChannel(), session.getFiber());
            }
        };

        Acceptor acceptor = createAcceptor(wrap(handlerFactory));

        Thread runner = new Thread(acceptor);
        runner.start();

        JetlangClient client = createClient();

        EventAssert<String> clientMsgReceive = new EventAssert<String>(2);
        Disposable unsubscribe1 = client.subscribe("newtopic", clientMsgReceive.asSubscribable());
        Disposable unsubscribe2 = client.subscribe("newtopic", clientMsgReceive.asSubscribable());
        client.start();

        subscriptionReceived.assertEvent();
        handler.publishToAllSubscribedClients("newtopic", "myclientmessage");
        clientMsgReceive.assertEvent();

        unsubscribe1.dispose();
        unsubscribe2.dispose();
        unsubscribeReceive.assertEvent();

        close(client);
        acceptor.stop();
    }

    @Test
    public void manyClientDisconnectWithoutReadTimeout() throws IOException, InterruptedException {
        final EventAssert<ReadTimeoutEvent> readTimeout = new EventAssert<ReadTimeoutEvent>(0);

        NewFiberSessionHandler handlerFactory = new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                readTimeout.subscribe(session.getReadTimeoutChannel(), session.getFiber());
            }
        };

        Acceptor acceptor = createAcceptor(wrap(handlerFactory));
        Thread runner = new Thread(acceptor);
        runner.start();

        int numClient = 10;
        final EventAssert<ConnectEvent> conected = new EventAssert<ConnectEvent>(numClient);
        final EventAssert<CloseEvent> closed = new EventAssert<CloseEvent>(numClient);
        List<JetlangClient> clients = new ArrayList<JetlangClient>();
        for (int i = 0; i < numClient; i++) {
            final JetlangClient client = createClient();
            conected.subscribe(client.getConnectChannel());
            closed.subscribe(client.getCloseChannel());
            readTimeout.subscribe(client.getReadTimeoutChannel());
            clients.add(client);
            client.start();
        }
        conected.assertEvent();
        for (JetlangClient client : clients) {
            assertTrue(client.close(true).await(30, TimeUnit.SECONDS));
        }
        closed.assertEvent();
        readTimeout.assertEvent();
        acceptor.stop();
    }

    @Test
    public void shouldContinueToReceiveMessagesAfterOneSubscriberLeaves() throws IOException {
        final EventAssert<SessionTopic> subscriptionReceived = new EventAssert<SessionTopic>(1);
        final EventAssert<String> unsubscribeReceive = new EventAssert<String>(1);

        NewFiberSessionHandler handlerFactory = new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptionReceived.subscribe(session.getSubscriptionRequestChannel(), session.getFiber());
                unsubscribeReceive.subscribe(session.getUnsubscribeChannel(), session.getFiber());
            }
        };

        final Acceptor acceptor = createAcceptor(wrap(handlerFactory));

        Thread runner = new Thread(acceptor);
        runner.start();

        final JetlangClient client = createClient();

        final AtomicBoolean firstReceivedAMessage = new AtomicBoolean();
        final EventAssert<String> secondSubscriber = new EventAssert<String>(0);
        final Disposable unsubscribe1 = client.subscribe("newtopic", new SynchronousDisposingExecutor(), new Callback<Object>() {
            public void onMessage(Object message) {
                firstReceivedAMessage.set(true);
            }
        });
        Disposable unsubscribe2 = client.subscribe("newtopic", secondSubscriber.asSubscribable());
        client.start();

        subscriptionReceived.assertEvent();

        unsubscribe1.dispose();

        handler.publishToAllSubscribedClients("newtopic", "shouldContinueToReceiveMessagesAfterOneSubscriberLeaves");

        secondSubscriber.assertEvent();
        unsubscribe2.dispose();
        unsubscribeReceive.assertEvent();
        assertFalse(firstReceivedAMessage.get());
        close(client);
        acceptor.stop();
    }

    @Test
    public void subscribeAfterStart() throws IOException, InterruptedException {
        final EventAssert<JetlangSession> openEvent = new EventAssert<JetlangSession>(1);
        final EventAssert<SessionTopic> subscriptions = EventAssert.create(1);
        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptions.subscribe(session.getSubscriptionRequestChannel());
                openEvent.receiveMessage(session);
            }
        });

        Acceptor acceptor = createAcceptor(sessionCallback);

        Thread runner = new Thread(acceptor);
        runner.start();

        EventAssert<Object> msgReceived = new EventAssert<Object>(1);
        JetlangClient client = createClient();
        client.subscribe("topic", msgReceived.asSubscribable());

        client.start();

        openEvent.assertEvent();
        subscriptions.assertEvent();
        handler.publishToAllSubscribedClients("topic", "mymsg");
        msgReceived.assertEvent();
        close(client);
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

        NewSessionHandler sessionCallback = wrap(new NewFiberSessionHandler() {
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
            }
        });
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
    public void regression() throws Exception {
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
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptionReceived.subscribe(session.getSubscriptionRequestChannel(), session.getFiber());
                logoutEvent.subscribe(session.getLogoutChannel(), session.getFiber());
                serverMessageReceive.subscribe(session.getSessionMessageChannel(), session.getFiber());
                unsubscribeReceive.subscribe(session.getUnsubscribeChannel(), session.getFiber());
                serverSessionClose.subscribe(session.getSessionCloseChannel(), session.getFiber());
                assertEquals(session.getSessionId(), session.getSessionId());
            }
        };

        Acceptor acceptor = createAcceptor(wrap(handlerFactory));

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

        LogoutResult closeLatch = client.close(true);

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

    @Test
    public void rawMsgRegression() throws Exception {
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
            public void onNewSession(ClientPublisher pub, JetlangFiberSession session) {
                subscriptionReceived.subscribe(session.getSubscriptionRequestChannel(), session.getFiber());
                logoutEvent.subscribe(session.getLogoutChannel(), session.getFiber());
                serverMessageReceive.subscribe(session.getSessionMessageChannel(), session.getFiber());
                unsubscribeReceive.subscribe(session.getUnsubscribeChannel(), session.getFiber());
                serverSessionClose.subscribe(session.getSessionCloseChannel(), session.getFiber());
                assertEquals(session.getSessionId(), session.getSessionId());
            }
        };

        Acceptor acceptor = createAcceptor(wrap(handlerFactory));
        Thread runner = new Thread(acceptor);
        runner.start();

        EventAssert<String> clientMsgReceive = new EventAssert<String>(1);
        JetlangClient client = createRawMsgClient(clientMsgReceive);
        EventAssert<ConnectEvent> clientConnect = EventAssert.expect(1, client.getConnectChannel());
        EventAssert<CloseEvent> clientClose = EventAssert.expect(1, client.getCloseChannel());

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

        LogoutResult closeLatch = client.close(true);

        assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
        logoutEvent.assertEvent();
        serverSessionClose.assertEvent();
        clientClose.assertEvent();
        assertEquals(CloseEvent.GracefulDisconnect.class, clientClose.received.take().getClass());
        assertEquals(0, handler.clientCount());
        acceptor.stop();
        service.shutdownNow();
    }

    private JetlangClient createRawMsgClient(EventAssert<String> clientMsgReceive) {
        ByteBuffer byteBuffer = NioReader.bufferAllocate(4096);
        return new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer(), new ErrorHandler.SysOut(), new RawMsgHandler() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public void onRawMsg(RawMsg rawMsg) {
                rawMsg.read(byteBuffer);

                byteBuffer.flip();
                int topicSize = byteBuffer.get();

                final char[] chars = new char[topicSize];
                for (int i = 0; i < topicSize; i++) {
                    chars[i] = (char) byteBuffer.get();
                }
                String topic = new String(chars);
                int size = byteBuffer.getInt();

                try {
                    String msg = (String) new ObjectInputStream(new ByteBufferInputStream(byteBuffer)).readObject();
                    clientMsgReceive.receiveMessage(msg);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }

                byteBuffer.clear();
            }
        });
    }

    private Acceptor createAcceptor(NewSessionHandler newSession) throws IOException {
        handler = new JetlangClientHandler(serializerFactory, newSession,
                service, sessionConfig, new JetlangClientHandler.FiberFactory.ThreadFiberFactory(),
                new ErrorHandler.SysOut());
        return new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);
    }

    public class ByteBufferInputStream extends InputStream {
        private final ByteBuffer buffer;

        public ByteBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int read() {
            return buffer.get() & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            final int pos = buffer.position();
            buffer.get(b, off, len);
            return buffer.position() - pos;
        }
    }
}

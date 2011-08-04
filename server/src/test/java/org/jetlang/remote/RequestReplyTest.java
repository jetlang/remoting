package org.jetlang.remote;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.acceptor.*;
import org.jetlang.remote.client.*;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.JavaSerializer;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RequestReplyTest {
    ExecutorService service = Executors.newCachedThreadPool();
    JetlangSessionConfig sessionConfig = new JetlangSessionConfig();
    JetlangClientConfig clientConfig = new JetlangClientConfig();

    SocketConnector conn = new SocketConnector("localhost", 8081);
    JetlangClientHandler handler;

    @After
    public void shutdown() {
        service.shutdownNow();
    }

    private void close(JetlangClient client) {
        try {
            assertTrue(client.close(true).await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

        int total = 999;
        JetlangClient client = createClient();
        client.start();
        for (int i = 0; i < total; i++) {
            EventAssert<String> reply = new EventAssert<String>(1);

            CallbackList<TimeoutControls> timeout = CallbackList.create();

            client.request("reqTopic",
                    "requestObject",
                    new SynchronousDisposingExecutor(), //target fiber
                    reply.createCallback(), //on reply
                    timeout, //on timeout
                    2, TimeUnit.SECONDS); //timeout

            reply.assertEvent();
            assertEquals("replyMsg", reply.takeFromReceived());
            assertEquals(0, timeout.received.size());
            if(i % 1000 == 0){
                System.out.println("i = " + i);
            }
        }
        close(client);
        acceptor.stop();
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

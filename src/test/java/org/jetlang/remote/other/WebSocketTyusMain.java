package org.jetlang.remote.other;

import org.glassfish.tyrus.client.ClientManager;
import org.jetlang.fibers.NioFiberImpl;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class WebSocketTyusMain {


    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException, DeploymentException {
        int sleepTimeOnFirstMessage = 0;

        int toSend = 5000000;

        int msgSize = 1;
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < msgSize; i++) {
            msg.append(" ");
        }
        String SENT_MESSAGE = msg.toString();

        NioFiberImpl acceptorFiber = new NioFiberImpl();
        acceptorFiber.start();
        CountDownLatch messageLatch = new CountDownLatch(toSend);

        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        ClientManager client = ClientManager.createClient();
        CountDownLatch onOPen = new CountDownLatch(1);
        Session s = client.connectToServer(new Endpoint() {

            @Override
            public void onOpen(Session session, EndpointConfig config) {
                onOPen.countDown();
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    boolean slept = false;

                    @Override
                    public void onMessage(String message) {
                        assertEquals(SENT_MESSAGE, message);
                        messageLatch.countDown();
                        if (!slept) {
                            try {
                                if (sleepTimeOnFirstMessage > 0) {
                                    Thread.sleep(sleepTimeOnFirstMessage);
                                }
                                slept = true;
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                });
            }
        }, cec, new URI("ws://localhost:8025/websockets/echo"));
        if (!onOPen.await(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Never connected");
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < toSend; i++) {
            s.getBasicRemote().sendText(SENT_MESSAGE);
        }
        if (!messageLatch.await(20, TimeUnit.MINUTES)) {
            System.out.println("Nothing received");
        }
        long end = System.currentTimeMillis();
        long msDuration = end - start;
        double microsPer = TimeUnit.MILLISECONDS.toMicros(msDuration) / toSend;
        System.out.println("microsPer = " + microsPer);
        s.close();
        client.shutdown();
        acceptorFiber.dispose();
    }
}

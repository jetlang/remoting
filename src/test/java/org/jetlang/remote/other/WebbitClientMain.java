package org.jetlang.remote.other;

import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebSocketConnection;
import org.webbitserver.netty.WebSocketClient;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class WebbitClientMain {


    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException, DeploymentException {
        int sleepTimeOnFirstMessage = 0;

        int toSend = 5000000;

        int msgSize = 1;
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < msgSize; i++) {
            msg.append(" ");
        }
        String SENT_MESSAGE = msg.toString();

        CountDownLatch messageLatch = new CountDownLatch(toSend);
        CountDownLatch onOPen = new CountDownLatch(1);

        AtomicReference<WebSocketConnection> conn = new AtomicReference<>();
        org.webbitserver.WebSocketHandler handler = new BaseWebSocketHandler() {
            @Override
            public void onOpen(WebSocketConnection connection) throws Exception {
                conn.set(connection);
                onOPen.countDown();
            }

            @Override
            public void onMessage(WebSocketConnection connection, String msg) throws Throwable {
                assertEquals(SENT_MESSAGE, msg);
                messageLatch.countDown();
            }
        };
        org.webbitserver.netty.WebSocketClient client = new WebSocketClient(new URI("ws://localhost:8025/websockets/echo"), handler);
        client.start();
        if (!onOPen.await(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Never connected");
        }
        WebSocketConnection theConn = conn.get();
        long start = System.currentTimeMillis();
        for (int i = 0; i < toSend; i++) {
            theConn.send(SENT_MESSAGE);
        }
        if (!messageLatch.await(20, TimeUnit.MINUTES)) {
            System.out.println("Nothing received");
        }
        long end = System.currentTimeMillis();
        long msDuration = end - start;
        double microsPer = TimeUnit.MILLISECONDS.toMicros(msDuration) / toSend;
        System.out.println("microsPer = " + microsPer);
        client.stop();
    }
}

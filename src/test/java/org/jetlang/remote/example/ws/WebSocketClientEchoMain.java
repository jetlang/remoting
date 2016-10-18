package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiberImpl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class WebSocketClientEchoMain {


    public static void main(String[] args) throws InterruptedException {
        int toSend = 5000000;

        int msgSize = 1;
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < msgSize; i++) {
            msg.append(" ");
        }
        String SENT_MESSAGE = msg.toString();

        NioFiberImpl clientFiber = new NioFiberImpl();
        clientFiber.start();
        CountDownLatch onOPend = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(toSend);
        WebSocketHandler<Void> clienthandler = new WebSocketHandler<Void>() {
            @Override
            public Void onOpen(WebSocketConnection connection) {
                onOPend.countDown();
                return null;
            }

            @Override
            public void onMessage(WebSocketConnection connection, Void state, String msg) {
                assertEquals(SENT_MESSAGE, msg);
                messageLatch.countDown();
            }

            @Override
            public void onClose(WebSocketConnection connection, Void state) {

            }

            @Override
            public void onError(WebSocketConnection connection, Void state, String msg) {

            }

            @Override
            public void onBinaryMessage(WebSocketConnection connection, Void state, byte[] result, int size) {

            }
        };
        WebSocketClient<Void> client = new WebSocketClient<>(clientFiber, "localhost", 8025,
                new WebSocketClient.Config(), clienthandler, "/websockets/echo");
        client.start();
        if (!onOPend.await(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Never connected");
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < toSend; i++) {
            SendResult send = client.send(SENT_MESSAGE);
            if (send instanceof SendResult.Buffered) {
                System.out.println("ClientSendBuffered = " + ((SendResult.Buffered) send).getTotalBufferedInBytes());
                Thread.sleep(1);
            }
        }
        if (!messageLatch.await(toSend, TimeUnit.SECONDS)) {
            System.out.println("Nothing received");
        }
        long end = System.currentTimeMillis();
        long msDuration = end - start;
        double microsPer = TimeUnit.MILLISECONDS.toMicros(msDuration) / toSend;
        System.out.println("microsPer = " + microsPer);
        client.stop();
        clientFiber.dispose();
    }
}

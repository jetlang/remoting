package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;

import javax.websocket.DeploymentException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class WebSocketEchoMain {


    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException, DeploymentException {
        int sleepTimeOnFirstMessage = 0;

        int toSend = 100;

        int msgSize = 1;
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < msgSize; i++) {
            msg.append(" ");
        }
        String SENT_MESSAGE = msg.toString();

        NioFiberImpl acceptorFiber = new NioFiberImpl();
        acceptorFiber.start();
        WebSocketHandler<Void> handler = new WebSocketHandler<Void>() {
            @Override
            public Void onOpen(WebSocketConnection connection) {
                System.out.println("Open!");
                return null;
            }

            @Override
            public void onMessage(WebSocketConnection connection, Void nothing, String msg) {
                SendResult send = connection.send(msg);
                if (send instanceof SendResult.Buffered) {
//                    try {
//                        //connection.close();
                    System.out.println("Buffered: " + ((SendResult.Buffered) send).getTotalBufferedInBytes());
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
                }
            }

            @Override
            public void onBinaryMessage(WebSocketConnection connection, Void state, byte[] result, int size) {
                connection.sendBinary(result, 0, size);
            }

            @Override
            public void onClose(WebSocketConnection connection, Void nothing) {
                System.out.println("WS Close");
            }

            @Override
            public void onError(WebSocketConnection connection, Void state, String msg) {
                System.err.println(msg);
            }
        };

        WebServerConfigBuilder config = new WebServerConfigBuilder();
        config.add("/websockets/echo", handler);
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("websocket.html");
        config.add("/", new StaticResource(new File(resource.getFile()).toPath()));

        final int cores = Runtime.getRuntime().availableProcessors();
        RoundRobinClientFactory readers = new RoundRobinClientFactory();
        List<NioFiber> allReadFibers = new ArrayList<>();
        for (int i = 0; i < cores; i++) {
            NioFiber readFiber = new NioFiberImpl();
            readFiber.start();
            readers.add(config.create(readFiber));
            allReadFibers.add(readFiber);
        }

        WebAcceptor.Config acceptorConfig = new WebAcceptor.Config();

        WebAcceptor acceptor = new WebAcceptor(8025, acceptorFiber, readers, acceptorConfig, () -> {
            System.out.println("AcceptorEnd");
        });

        acceptor.start();
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
        WebSocketClient<Void> client = new WebSocketClient<>(acceptorFiber, "localhost", 8025,
                new WebSocketClient.Config(), clienthandler, "/websockets/echo");
        client.start();
        if (!onOPend.await(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Never connected");
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < toSend; i++) {
            SendResult send = client.send(SENT_MESSAGE);
            if (send instanceof SendResult.Buffered) {
                System.out.println("Buffered = " + ((SendResult.Buffered) send).getTotalBufferedInBytes());
            }
        }
        if (!messageLatch.await(toSend, TimeUnit.SECONDS)) {
            System.out.println("Nothing received");
        }
        long end = System.currentTimeMillis();
        long msDuration = end - start;
        double perMs = toSend / msDuration;
        System.out.println("perMs = " + perMs);
        System.out.println(perMs * 1000);
        client.stop();

        allReadFibers.forEach(NioFiber::dispose);
        acceptorFiber.dispose();
    }
}

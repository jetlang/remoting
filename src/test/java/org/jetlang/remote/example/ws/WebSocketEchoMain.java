package org.jetlang.remote.example.ws;

import org.glassfish.tyrus.client.ClientManager;
import org.jetlang.fibers.NioFiberImpl;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class WebSocketEchoMain {


    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException, DeploymentException {
        int toSend = 1000000;

        int msgSize = 100;
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
                if (connection.send(msg) instanceof SendResult.Buffered) {
//                    try {
//                        //connection.close();
//                        System.out.println("Closed on buffer.");
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
                }
            }

            @Override
            public void onClose(WebSocketConnection connection, Void nothing) {
                System.out.println("WS Close");
            }
        };

        WebServerConfigBuilder config = new WebServerConfigBuilder();
        config.add("/websockets/echo", handler);
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("websocket.html");
        config.add("/", new StaticResource(new File(resource.getFile()).toPath()));

        WebAcceptor acceptor = new WebAcceptor(8025, acceptorFiber, config.create(), () -> {
        });
        acceptor.start();
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
                                Thread.sleep(3000);
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
        double perMs = toSend / msDuration;
        System.out.println("perMs = " + perMs);
        System.out.println(perMs * 1000);
        client.shutdown();
        //Thread.sleep(Long.MAX_VALUE);
        acceptorFiber.dispose();
    }
}

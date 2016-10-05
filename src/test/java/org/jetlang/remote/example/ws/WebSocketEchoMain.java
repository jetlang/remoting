package org.jetlang.remote.example.ws;

import org.glassfish.tyrus.client.ClientManager;
import org.jetlang.fibers.NioFiberImpl;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebSocketEchoMain {

    private static final String SENT_MESSAGE = "Hello World";

    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException, DeploymentException {
        NioFiberImpl acceptorFiber = new NioFiberImpl();
        acceptorFiber.start();
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void onOpen(WebSocketConnection connection) {
                System.out.println("Open!");
            }

            @Override
            public void onMessage(WebSocketConnection connection, String msg) {
                System.out.println("msg = " + msg);
            }
        };
        WebSocketClientFactory factory = new WebSocketClientFactory(handler);
        WebSocketAcceptor acceptor = new WebSocketAcceptor(8025, acceptorFiber, factory, () -> {
        });
        acceptor.start();
        CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {

            @Override
            public void onOpen(Session session, EndpointConfig config) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String message) {
                            System.out.println("Received message: " + message);
                            messageLatch.countDown();
                        }
                    });
                    session.getBasicRemote().sendText(SENT_MESSAGE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, cec, new URI("ws://localhost:8025/websockets/echo"));
        if (!messageLatch.await(5, TimeUnit.SECONDS)) {
            System.out.println("Nothing received");
        }
        acceptorFiber.dispose();
    }
}

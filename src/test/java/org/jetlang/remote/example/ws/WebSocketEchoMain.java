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
                connection.send(msg);
            }

            @Override
            public void onClose(WebSocketConnection connection) {
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
                            System.out.println("Received message: '" + message + "'");
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
        client.shutdown();
        Thread.sleep(Long.MAX_VALUE);
        acceptorFiber.dispose();
    }
}

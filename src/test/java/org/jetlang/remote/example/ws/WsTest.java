package org.jetlang.remote.example.ws;

import org.glassfish.tyrus.client.ClientManager;
import org.jetlang.fibers.NioFiberImpl;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WsTest {

    private static final String SENT_MESSAGE = "Hello World";

    public static void main(String [] args){
        NioFiberImpl acceptorFiber = new NioFiberImpl();
        acceptorFiber.start();
        try {

            WsClientFactory factory = new WsClientFactory() {
            };
            WebSocketAcceptor acceptor = new WebSocketAcceptor(8025, acceptorFiber, factory, ()->{});
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
                                System.out.println("Received message: "+message);
                                messageLatch.countDown();
                            }
                        });
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/echo"));
            messageLatch.await(100, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        acceptorFiber.dispose();
    }
}

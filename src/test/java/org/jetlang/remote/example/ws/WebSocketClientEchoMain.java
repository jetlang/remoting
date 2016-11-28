package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.web.HttpRequest;
import org.jetlang.web.SessionFactory;
import org.jetlang.web.WebSocketClient;
import org.jetlang.web.WebSocketConnection;
import org.jetlang.web.WebSocketHandler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebSocketClientEchoMain {


    public static void main(String[] args) throws InterruptedException, URISyntaxException {
        NioFiberImpl clientFiber = new NioFiberImpl();
        clientFiber.start();
        WebSocketHandler<Map<String, Object>, Void> clienthandler = new WebSocketHandler<Map<String, Object>, Void>() {
            @Override
            public Void onOpen(WebSocketConnection connection, HttpRequest headers, Map<String, Object> state) {
                System.out.println("WebSocketClientEchoMain.onOpen");
                connection.send("Hello World");
                return null;
            }

            @Override
            public void onMessage(WebSocketConnection connection, Void state, String msg) {
                System.out.println("msg = " + msg);
            }

            @Override
            public void onClose(WebSocketConnection connection, Void state) {
                System.out.println("WebSocketClientEchoMain.onClose");
            }

            @Override
            public void onError(WebSocketConnection connection, Void state, String msg) {
                System.err.println(msg);
            }

            @Override
            public void onException(WebSocketConnection connection, Void state, Exception failed) {
                System.err.println(failed.getMessage());
                failed.printStackTrace(System.err);
            }

            @Override
            public void onBinaryMessage(WebSocketConnection connection, Void state, byte[] result, int size) {

            }
        };
        WebSocketClient<Map<String, Object>, Void> client = new WebSocketClient<Map<String, Object>, Void>(clientFiber, new URI("ws://localhost:8025/websockets/echo"),
                new WebSocketClient.Config(), clienthandler, SessionFactory.mapPerClient());
        CountDownLatch start = client.start();
        if (!start.await(60, TimeUnit.SECONDS)) {
            client.stop();
            throw new RuntimeException("Never connected");
        } else {
            System.out.println("start = " + start);
        }
        Thread.sleep(Long.MAX_VALUE);
        client.stop();
        clientFiber.dispose();
    }
}

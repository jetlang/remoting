package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiberImpl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebSocketClientEchoMain {


    public static void main(String[] args) throws InterruptedException {
        NioFiberImpl clientFiber = new NioFiberImpl();
        clientFiber.start();
        WebSocketHandler<Void> clienthandler = new WebSocketHandler<Void>() {
            @Override
            public Void onOpen(WebSocketConnection connection) {
                System.out.println("WebSocketClientEchoMain.onOpen");
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

            }

            @Override
            public void onBinaryMessage(WebSocketConnection connection, Void state, byte[] result, int size) {

            }
        };
        WebSocketClient<Void> client = new WebSocketClient<>(clientFiber, "localhost", 8025,
                new WebSocketClient.Config(), clienthandler, "/websockets/echo");
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

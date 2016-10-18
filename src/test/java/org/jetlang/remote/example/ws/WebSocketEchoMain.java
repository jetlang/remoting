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

public class WebSocketEchoMain {


    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException, DeploymentException {

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

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                allReadFibers.forEach(NioFiber::dispose);
                acceptorFiber.dispose();
            }
        });
        Thread.sleep(Long.MAX_VALUE);
    }
}

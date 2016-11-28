package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.fibers.PoolFiberFactory;
import org.jetlang.web.HttpRequest;
import org.jetlang.web.RoundRobinClientFactory;
import org.jetlang.web.SendResult;
import org.jetlang.web.SessionDispatcherFactory;
import org.jetlang.web.SessionFactory;
import org.jetlang.web.StaticHtml;
import org.jetlang.web.WebAcceptor;
import org.jetlang.web.WebServerConfigBuilder;
import org.jetlang.web.WebSocketConnection;
import org.jetlang.web.WebSocketHandler;

import java.io.File;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebSocketServerEchoMain {

    static class MyConnectionState {
        final SocketChannel channel;
        final Date created = new Date();

        public MyConnectionState(SocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public String toString() {
            return "MyConnectionState{" +
                    "channel=" + channel +
                    ", created=" + created +
                    '}';
        }
    }

    static class MyWebsocketState {

        private final MyConnectionState httpSessionState;

        public MyWebsocketState(MyConnectionState httpSessionState) {
            this.httpSessionState = httpSessionState;
        }

        @Override
        public String toString() {
            return "MyWebsocketState{" +
                    "httpSessionState=" + httpSessionState +
                    '}';
        }
    }


    public static void main(String[] args) throws InterruptedException {

        NioFiberImpl acceptorFiber = new NioFiberImpl();
        acceptorFiber.start();
        WebSocketHandler<MyConnectionState, MyWebsocketState> handler = new WebSocketHandler<MyConnectionState, MyWebsocketState>() {
            @Override
            public MyWebsocketState onOpen(WebSocketConnection connection, HttpRequest headers, MyConnectionState httpSessionState) {
                System.out.println("Open!");
                return new MyWebsocketState(httpSessionState);
            }

            @Override
            public void onMessage(WebSocketConnection connection, MyWebsocketState wsState, String msg) {
                SendResult send = connection.send(msg);
                if (send instanceof SendResult.Buffered) {
                    System.out.println("Buffered: " + ((SendResult.Buffered) send).getTotalBufferedInBytes());
                }
            }

            @Override
            public void onBinaryMessage(WebSocketConnection connection, MyWebsocketState state, byte[] result, int size) {
                connection.sendBinary(result, 0, size);
            }

            @Override
            public void onClose(WebSocketConnection connection, MyWebsocketState nothing) {
                System.out.println("WS Close: " + nothing);
            }

            @Override
            public void onError(WebSocketConnection connection, MyWebsocketState state, String msg) {
                System.err.println(msg);
            }

            @Override
            public void onException(WebSocketConnection connection, MyWebsocketState state, Exception failed) {
                System.err.print(failed.getMessage());
                failed.printStackTrace(System.err);
            }
        };

        SessionFactory<MyConnectionState> fact = new SessionFactory<MyConnectionState>() {
            @Override
            public MyConnectionState create(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequest headers) {
                return new MyConnectionState(channel);
            }

            @Override
            public void onClose(MyConnectionState session) {
                System.out.println("session closed = " + session);
            }
        };
        WebServerConfigBuilder<MyConnectionState> config = new WebServerConfigBuilder<>(fact);

        //half of the cores for reading from sockets and half for handling requests.
        //this will give very good throughput, but isn't ideal for every use case.
        final int halfOfCores = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);

        ExecutorService executorService = Executors.newFixedThreadPool(halfOfCores);
        //Each tcp connection will be given a pool fiber which will serialize incoming http or web requests.
        PoolFiberFactory poolFiberFactory = new PoolFiberFactory(executorService);
        config.setDispatcher(new SessionDispatcherFactory.FiberSessionFactory<MyConnectionState>(poolFiberFactory));

        config.add("/websockets/echo", handler);
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("websocket.html");
        config.add("/", new StaticHtml<>(new File(resource.getFile()).toPath()));

        RoundRobinClientFactory readers = new RoundRobinClientFactory();
        List<NioFiber> allReadFibers = new ArrayList<>();
        for (int i = 0; i < halfOfCores; i++) {
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
                executorService.shutdownNow();
            }
        });
        Thread.sleep(Long.MAX_VALUE);
    }
}

package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.web.HandlerLocator;
import org.jetlang.web.HttpRequest;
import org.jetlang.web.SendResult;
import org.jetlang.web.SessionFactory;
import org.jetlang.web.WebAcceptor;
import org.jetlang.web.WebDispatcher;
import org.jetlang.web.WebServerConfigBuilder;
import org.jetlang.web.WebSocketConnection;
import org.jetlang.web.WebSocketHandler;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.Date;

import static org.jetlang.web.PathMatcher.pathEq;

/**
 * Creates a web server including web sockets using a single nio thread.
 * This can be ideal if clients need to share state. With a single
 * read thread locking of shared data structures is not necessary.
 * <p>
 * As an alternative to one big VM, the single threaded server can scale by deploying
 * lots of instances to a single server.
 * <p>
 * Always keep in mind that inbound http/websocket traffic is received on a single thread, so it is best to
 * avoid slow operations on this thread (e.g. database query).  Slow traffic needs to be offloaded to another thread
 * or use the pool fiber approach to dispatch events on a thread pool.
 */
public class WebSingleThreadedServerEchoMain {

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


    public static void main(String[] args) throws InterruptedException, URISyntaxException {

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
            public void onUnknownException(Throwable processingException, SocketChannel channel) {
                processingException.printStackTrace(System.err);
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

        config.add(pathEq("/websockets/echo"), handler);
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("web");
        config.add(new HandlerLocator.ResourcesDirectory<>(Paths.get(resource.toURI())));

        NioFiber readFiber = new NioFiberImpl();
        readFiber.start();
        WebDispatcher<MyConnectionState> dispatcher = config.create(readFiber);

        WebAcceptor.Config acceptorConfig = new WebAcceptor.Config();

        WebAcceptor acceptor = new WebAcceptor(8025, readFiber, dispatcher, acceptorConfig, () -> {
            System.out.println("AcceptorEnd");
        });

        acceptor.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                readFiber.dispose();
            }
        });
        Thread.sleep(Long.MAX_VALUE);
    }
}

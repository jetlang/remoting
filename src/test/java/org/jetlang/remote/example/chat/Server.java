package org.jetlang.remote.example.chat;

import org.jetlang.core.Callback;
import org.jetlang.core.RunnableExecutorImpl;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.acceptor.JetlangNioSession;
import org.jetlang.remote.acceptor.JetlangSessionConfig;
import org.jetlang.remote.acceptor.NioAcceptorHandler;
import org.jetlang.remote.acceptor.NioJetlangRemotingClientFactory;
import org.jetlang.remote.acceptor.NioJetlangSendFiber;
import org.jetlang.remote.acceptor.SessionMessage;
import org.jetlang.remote.core.ByteArraySerializer;
import org.jetlang.remote.core.Serializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class Server {

    public static void main(String[] args) throws IOException {
        int port = 8081;
        if (args.length == 1)
            port = Integer.parseInt(args[0]);

        final NioFiber nioFiber = new NioFiberImpl();

        //create send fiber as non-daemon thread to prevent main from exiting
        final Fiber sendFiber = new ThreadFiber(new RunnableExecutorImpl(), "sendFiber", false);
        ByteArraySerializer.Factory factory = new ByteArraySerializer.Factory();
        final Serializer serializer = factory.create();
        final Charset charset = Charset.forName("ASCII");
        NioJetlangSendFiber sender = new NioJetlangSendFiber(sendFiber, nioFiber, serializer.getWriter(), charset, new NioFiberImpl.NoOpBuffer());

        NioJetlangRemotingClientFactory.Handler sessions = new NioJetlangRemotingClientFactory.Handler() {
            @Override
            public void onNewSession(JetlangNioSession session) {
                System.out.println("Connect:" + session.getSessionId());
                Callback<SessionMessage<?>> onMsg = new Callback<SessionMessage<?>>() {
                    @Override
                    public void onMessage(SessionMessage sessionMessage) {
                        //forward the bytes to any and all clients that have subscribed to the topic
                        //message is serialized and written to clients on send fiber
                        sender.publishToAllSubscribedClients(sessionMessage.getTopic(), sessionMessage.getMessage());
                        System.out.println("topic: " + sessionMessage.getTopic() + " msg: " + sessionMessage.getMessage());
                    }
                };

                //receive messages on nio read thread
                session.getSessionMessageChannel().subscribe(new SynchronousDisposingExecutor(), onMsg);
                session.getSessionCloseChannel().subscribe(new SynchronousDisposingExecutor(), Client.print("Close: " + session.getSessionId()));
            }

            @Override
            public void onUnhandledReplyMsg(SelectionKey key, SocketChannel channel, String dataTopicVal, Object readObject) {
                System.err.println("onUnhandledReplyMsg " + dataTopicVal + " " + readObject);
            }

            @Override
            public void onUnknownMessage(SelectionKey key, SocketChannel channel, int read) {
                System.err.println("onUnknownMessage " + read + " on " + key);
            }

            @Override
            public void onHandlerException(Exception failed) {
                failed.printStackTrace();
            }
        };

        final ServerSocketChannel socketChannel = ServerSocketChannel.open();
        final InetSocketAddress address = new InetSocketAddress(port);
        socketChannel.socket().bind(address);
        socketChannel.configureBlocking(false);
        final NioJetlangRemotingClientFactory acceptor = new NioJetlangRemotingClientFactory(serializer, new JetlangSessionConfig(), sessions, sender, charset);
        nioFiber.addHandler(new NioAcceptorHandler(socketChannel, acceptor, () -> System.out.println("AcceptorEnd")));
        nioFiber.start();
        sendFiber.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                nioFiber.dispose();
                sendFiber.dispose();
            }
        });
    }
}

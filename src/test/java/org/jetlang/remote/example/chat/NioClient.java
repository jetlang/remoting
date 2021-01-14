package org.jetlang.remote.example.chat;

import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.acceptor.NioJetlangSendFiber;
import org.jetlang.remote.client.CloseEvent;
import org.jetlang.remote.client.ConnectEvent;
import org.jetlang.remote.client.JetlangClientConfig;
import org.jetlang.remote.client.JetlangTcpNioClient;
import org.jetlang.remote.client.SocketConnector;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.JavaSerializer;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.remote.core.TopicReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class NioClient {


    public static void main(String[] args) throws IOException, InterruptedException {
        String host = "localhost";
        int port = 8081;
        if (args.length == 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        SocketConnector conn = new SocketConnector(host, port);
        JetlangClientConfig clientConfig = new JetlangClientConfig();

        NioFiberImpl nioFiber = new NioFiberImpl();
        nioFiber.start();
        TcpClientNioFiber tcpNio = new TcpClientNioFiber(nioFiber);
        Fiber fiber = new ThreadFiber();
        fiber.start();
        JavaSerializer javaSerializer = new JavaSerializer();
        NioJetlangSendFiber<Object> sendFiber = new NioJetlangSendFiber<>(fiber, nioFiber, javaSerializer.getWriter(), StandardCharsets.UTF_8, new NioFiberImpl.NoOpBuffer());
        TopicReader.Cached topicReader = new TopicReader.Cached(StandardCharsets.UTF_8);
        JetlangTcpNioClient<Object, Object> tcpClient = new JetlangTcpNioClient<>(conn, sendFiber, clientConfig,
                javaSerializer, new ErrorHandler.SysOut(), tcpNio, topicReader);
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        tcpClient.getConnectChannel().subscribe(executor, NioClient.<ConnectEvent>print("Connect"));
        tcpClient.getCloseChannel().subscribe(executor, NioClient.<CloseEvent>print("Closed"));
        Disposable client = tcpClient.start();
        read(tcpClient, executor);

        client.dispose();
        fiber.dispose();
        nioFiber.dispose();
    }

    public static void read(JetlangTcpNioClient<Object, Object> tcpClient, SynchronousDisposingExecutor executor) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String[] parts = line.split(" ");
            String command = parts[0];
            if ("exit".equalsIgnoreCase(command)) {
                break;
            } else if ("subscribe".equalsIgnoreCase(command)) {
                final String topic = parts[1];
                Callback<Object> msg = o -> System.out.println(topic + ": " + o);
                tcpClient.subscribe(topic, executor, msg);
            } else if ("publish".equalsIgnoreCase(command)) {
                String topic = parts[1];
                String msg = parts[2];
                tcpClient.publish(topic, msg);
            } else {
                System.out.println("Unknown command: " + line);
            }
        }
    }

    public static <T> Callback<T> print(final String connect) {
        return t -> System.out.println(connect);
    }
}

package com.jetlang.remote.example.chat;

import com.jetlang.remote.client.*;
import com.jetlang.remote.core.JavaSerializer;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Client {


    public static void main(String[] args) throws IOException, InterruptedException {
        String host = "localhost";
        int port = 8081;
        if (args.length == 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        SocketConnector conn = new SocketConnector(host, port);
        JetlangClientConfig clientConfig = new JetlangClientConfig();

        JetlangTcpClient tcpClient = new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer(), new JetlangTcpClient.ErrorHandler.SysOut());
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        tcpClient.getConnectChannel().subscribe(executor, Client.<ConnectEvent>print("Connect"));
        tcpClient.getDisconnectChannel().subscribe(executor, Client.<DisconnectEvent>print("Disconnect"));
        tcpClient.getCloseChannel().subscribe(executor, Client.<CloseEvent>print("Closed"));
        tcpClient.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            String[] parts = line.split(" ");
            String command = parts[0];
            if ("exit".equalsIgnoreCase(command)) {
                break;
            } else if ("subscribe".equalsIgnoreCase(command)) {
                final String topic = parts[1];
                Callback<Object> msg = new Callback<Object>() {

                    public void onMessage(Object o) {
                        System.out.println(topic + ": " + o);
                    }
                };
                tcpClient.subscribe(topic, executor, msg);
            } else if ("publish".equalsIgnoreCase(command)) {
                String topic = parts[1];
                String msg = parts[2];
                tcpClient.publish(topic, msg);
            } else {
                System.out.println("Unknown command: " + line);
            }
        }

        tcpClient.close(true).await(1, TimeUnit.SECONDS);
    }

    public static <T> Callback<T> print(final String connect) {
        return new Callback<T>() {

            public void onMessage(T t) {
                System.out.println(connect);
            }
        };
    }
}

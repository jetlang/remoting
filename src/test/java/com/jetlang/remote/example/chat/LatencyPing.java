package com.jetlang.remote.example.chat;

import com.jetlang.remote.client.*;
import com.jetlang.remote.core.JavaSerializer;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: mrettig
 * Date: 4/26/11
 * Time: 4:00 PM
 */
public class LatencyPing {

    private static volatile CountDownLatch latch;

    private static byte[] createMsg() {
        int length = (int) (Math.random() * 300);
        return new byte[length];
    }

    public static class Msg implements Serializable, Runnable {

        private long create = System.nanoTime();
        private final byte[] payload = createMsg();
        private final int sleepTime;

        public Msg(int sleepTime) {
            this.sleepTime = sleepTime;
        }

        public void run() {
            log("send");
        }

        private void log(String send) {
            long sendTime = System.nanoTime() - create;
            long ms = TimeUnit.NANOSECONDS.toMillis(sendTime);
            if (ms > 2) {
                System.out.println(send + " ms = " + ms + " size: " + payload.length + " sleep: " + sleepTime);
            }
        }

        public void logRoundTripLatency() {
            log("roundtrip");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 8081;
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        int iteration = 10;
        if (args.length >= 3) {
            iteration = Integer.parseInt(args[2]);
        }
        latch = new CountDownLatch(iteration * 2);
        SocketConnector conn = new SocketConnector(host, port);
        JetlangClientConfig clientConfig = new JetlangClientConfig();

        JetlangTcpClient tcpClient = new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer(), new JetlangTcpClient.ErrorHandler.SysOut());
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        tcpClient.getConnectChannel().subscribe(executor, Client.<ConnectEvent>print("Connect"));
        tcpClient.getDisconnectChannel().subscribe(executor, Client.<DisconnectEvent>print("Disconnect"));
        tcpClient.getCloseChannel().subscribe(executor, Client.<CloseEvent>print("Closed"));
        tcpClient.start();

        Callback<Msg> onMsg = new Callback<Msg>() {
            public void onMessage(Msg message) {
                message.logRoundTripLatency();
                latch.countDown();
            }
        };
        tcpClient.subscribe("t", new SynchronousDisposingExecutor(), onMsg);

        int sleepTime = 0;
        for (int i = 0; i < iteration; i++) {
            Thread.sleep(sleepTime);
            Msg msg = new Msg(sleepTime);
            tcpClient.publish("t", msg, msg);
            Msg second = new Msg(0);
            tcpClient.publish("t", second, second);
            sleepTime = (int) (Math.random() * 500.00);
        }
        System.out.println("executor = " + latch.await(10, TimeUnit.SECONDS));
        tcpClient.close(true).await(1, TimeUnit.SECONDS);
    }

}

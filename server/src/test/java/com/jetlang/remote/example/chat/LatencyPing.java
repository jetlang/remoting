package com.jetlang.remote.example.chat;

import com.jetlang.remote.client.*;
import com.jetlang.remote.core.ErrorHandler;
import com.jetlang.remote.core.JavaSerializer;
import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: mrettig
 * Date: 4/26/11
 * Time: 4:00 PM
 */
public class LatencyPing {

    private static int globalId;
    private static volatile CountDownLatch latch;
    private static Map<String, Msg> msgs = Collections.synchronizedMap(new HashMap<String, Msg>());

    private static byte[] createMsg() {
        int length = (int) (Math.random() * 300);
        return new byte[length];
    }

    public static class Content implements Serializable {
        private final byte[] payload = createMsg();
        public final String id = "MSGID" + String.valueOf(globalId++);
    }

    public static class Msg implements Serializable, Runnable {

        private final long create = System.nanoTime();
        private final int sleepTime;

        public final Content data = new Content();
        private volatile Date sentTimeStamp;

        public Msg(int sleepTime) {
            this.sleepTime = sleepTime;
        }

        public void run() {
            sentTimeStamp = new Date();
            log("send");
        }

        private void log(String send) {
            long sendTime = System.nanoTime() - create;
            long ms = TimeUnit.NANOSECONDS.toMillis(sendTime);
            if (ms > 2) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                String sentTime = sentTimeStamp != null ? format.format(sentTimeStamp) : "SentNotSet";
                System.out.println(data.id + " " + format.format(new Date()) + " " + send + " ms = " + ms + " size: " + data.payload.length + " sleep: " + sleepTime + " sent: " + sentTime);
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
        System.out.println("iterations = " + iteration);
        latch = new CountDownLatch(iteration * 2);
        SocketConnector conn = new SocketConnector(host, port);
        JetlangClientConfig clientConfig = new JetlangClientConfig();

        JetlangTcpClient tcpClient = new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new JavaSerializer(), new ErrorHandler.SysOut());
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        tcpClient.getConnectChannel().subscribe(executor, Client.<ConnectEvent>print("Connect"));
        tcpClient.getCloseChannel().subscribe(executor, Client.<CloseEvent>print("Closed"));
        tcpClient.start();

        Callback<Content> onMsg = new Callback<Content>() {
            public void onMessage(Content message) {
                Msg m = msgs.remove(message.id);
                m.logRoundTripLatency();
                latch.countDown();
            }
        };
        tcpClient.subscribe("t", new SynchronousDisposingExecutor(), onMsg);

        int sleepTime = 0;
        for (int i = 0; i < iteration; i++) {
            Thread.sleep(sleepTime);
            Msg msg = new Msg(sleepTime);
            msgs.put(msg.data.id, msg);
            tcpClient.publish("t", msg.data, msg);
            Msg second = new Msg(0);
            msgs.put(second.data.id, second);
            tcpClient.publish("t", second.data, second);
            sleepTime = (int) (Math.random() * 500.00);
        }
        System.out.println("executor = " + latch.await(10, TimeUnit.SECONDS));
        tcpClient.close(true).await(1, TimeUnit.SECONDS);
    }

}

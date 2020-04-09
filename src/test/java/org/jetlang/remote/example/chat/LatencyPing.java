package org.jetlang.remote.example.chat;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.client.CloseEvent;
import org.jetlang.remote.client.ConnectEvent;
import org.jetlang.remote.client.JetlangClientConfig;
import org.jetlang.remote.client.JetlangTcpClient;
import org.jetlang.remote.client.SocketConnector;
import org.jetlang.remote.core.ByteMessageWriter;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.ObjectByteReader;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.Serializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: mrettig
 * Date: 4/26/11
 * Time: 4:00 PM
 */
public class LatencyPing {

    private static volatile CountDownLatch latch;

    public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 8081;
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        final int iteration = 50000;
        System.out.println("iterations = " + iteration);
        latch = new CountDownLatch(1);
        SocketConnector conn = new SocketConnector(host, port);
        JetlangClientConfig clientConfig = new JetlangClientConfig();

        JetlangTcpClient tcpClient = new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new LongSerializer(), new ErrorHandler.SysOut());
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        tcpClient.getConnectChannel().subscribe(executor, Client.<ConnectEvent>print("Connect"));
        tcpClient.getCloseChannel().subscribe(executor, Client.<CloseEvent>print("Closed"));
        tcpClient.start();

        Callback<Long> onMsg = new Callback<Long>() {
            int count = 0;
            long latency = 0;
            long min = Long.MAX_VALUE;
            long max = 0;
            public void onMessage(Long message) {
                long duration = System.nanoTime() - message;
                min = Math.min(duration, min);
                max = Math.max(duration, max);
                count++;
                latency+= duration;
                if(count == iteration){
                    System.out.println("Min: " + min + " Max: " + max);
                    System.out.println("Count: " + count);
                    System.out.println("AvgNanos: " + (latency/count));
                    latch.countDown();
                }
            }
        };
        tcpClient.subscribe("t", new SynchronousDisposingExecutor(), onMsg);

        int sleepTime = 1;
        for (int i = 0; i < iteration; i++) {
            tcpClient.publish("t", System.nanoTime());
            Thread.sleep(sleepTime);
        }
        System.out.println("executor = " + latch.await(10, TimeUnit.SECONDS));
        tcpClient.close(true).await(1, TimeUnit.SECONDS);
    }

    public static class LongSerializer implements Serializer<Long, Long> {

        public ObjectByteWriter<Long> getWriter() {
            return new ObjectByteWriter<Long>() {
                byte[] bytes = new byte[8];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                public void write(String topic, Long msg, ByteMessageWriter writer) {
                    buffer.clear();
                    buffer.putLong(msg);
                    writer.writeObjectAsBytes(bytes, 0, bytes.length);
                }
            };
        }

        public ObjectByteReader<Long> getReader() {
            return new ObjectByteReader<Long>() {
                public Long readObject(String fromTopic, byte[] buffer, int offset, int length) {
                    final ByteBuffer wrap = ByteBuffer.wrap(buffer);
                    if(offset > 0){
                        wrap.position(offset);
                    }
                    return wrap.getLong();
                }
            };
        }
    }

}

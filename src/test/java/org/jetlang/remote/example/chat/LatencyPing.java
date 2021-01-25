package org.jetlang.remote.example.chat;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.client.CloseEvent;
import org.jetlang.remote.client.ConnectEvent;
import org.jetlang.remote.client.JetlangClientConfig;
import org.jetlang.remote.client.JetlangTcpClient;
import org.jetlang.remote.client.JetlangTcpNioClient;
import org.jetlang.remote.client.SocketConnector;
import org.jetlang.remote.core.ByteMessageWriter;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.ObjectByteReader;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.remote.core.TopicReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: mrettig
 * Date: 4/26/11
 * Time: 4:00 PM
 */
public class LatencyPing {

    public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 8081;
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        final int iteration = 50000;
        final int loops = 1;
        System.out.println("iterations = " + iteration + " loops: " + loops);
        for(int i = 0; i < loops; i++) {
            execute(host, port, iteration);
        }
    }

    private static void execute(String host, int port, int iteration) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        SocketConnector conn = new SocketConnector(host, port);
        JetlangClientConfig clientConfig = new JetlangClientConfig();

        Client tcpClient = startNioClient(conn, clientConfig);

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
                latency += duration;
                if (count == iteration) {
                    System.out.println("Min: " + min + " Max: " + max);
                    System.out.println("Count: " + count);
                    System.out.println("AvgNanos: " + (latency / count));
                    latch.countDown();
                }
            }
        };
        tcpClient.subscribeOnReadThread("t", onMsg);

        int sleepTime = 1;
        for (int i = 0; i < iteration; i++) {
            tcpClient.publish("t", System.nanoTime());
            Thread.sleep(sleepTime);
        }
        System.out.println("executor = " + latch.await(10, TimeUnit.SECONDS));
        tcpClient.close();
    }

    interface Client {

        void subscribeOnReadThread(String t, Callback<Long> onMsg);

        void publish(String t, long nanoTime);

        void close() throws InterruptedException;

    }

    private static Client startClient(SocketConnector conn, JetlangClientConfig clientConfig) {
        JetlangTcpClient tcpClient = new JetlangTcpClient(conn, new ThreadFiber(), clientConfig, new LongSerializer(), new ErrorHandler.SysOut());
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        tcpClient.getConnectChannel().subscribe(executor, (msg) -> {
            System.out.println("msg = " + msg);
        });
        tcpClient.getCloseChannel().subscribe(executor, (msg) -> {
            System.out.println("msg = " + msg);
        });
        tcpClient.start();
        return new Client() {
            @Override
            public void subscribeOnReadThread(String topic, Callback<Long> onMsg) {
                tcpClient.subscribe(topic, executor, onMsg);
            }

            @Override
            public void publish(String t, long nanoTime) {
                tcpClient.publish(t, nanoTime);
            }

            @Override
            public void close() throws InterruptedException {
                tcpClient.close(true).await(1, TimeUnit.SECONDS);
            }
        };
    }

    private static Client startNioClient(SocketConnector conn, JetlangClientConfig clientConfig) throws InterruptedException {
        TopicReader.Cached topicReader = new TopicReader.Cached(StandardCharsets.US_ASCII);
        NioFiber nioFiber = new NioFiberImpl();
        nioFiber.start();
        TcpClientNioFiber readFiber = new TcpClientNioFiber(nioFiber);
        JetlangTcpNioClient<Long, Long> tcpClient = new JetlangTcpNioClient<>(conn, clientConfig, new LongSerializer(),
                new ErrorHandler.SysOut(), readFiber, topicReader);
        SynchronousDisposingExecutor executor = new SynchronousDisposingExecutor();
        CountDownLatch connect = new CountDownLatch(1);
        tcpClient.getConnectChannel().subscribe(executor, (msg) -> {
            System.out.println("msg = " + msg);
            connect.countDown();
        });
        tcpClient.getCloseChannel().subscribe(executor, (msg) -> {
            System.out.println("msg = " + msg);
        });
        tcpClient.start();
        boolean connected = connect.await(5, TimeUnit.SECONDS);
        if (!connected) {
            throw new RuntimeException("Failed to connect");
        }
        return new Client() {

            @Override
            public void subscribeOnReadThread(String topic, Callback<Long> onMsg) {
                tcpClient.subscribeOnReadThread(topic, onMsg);
            }

            @Override
            public void publish(String t, long nanoTime) {
                tcpClient.publish(t, nanoTime);
            }

            @Override
            public void close() throws InterruptedException {
                tcpClient.stop(1, TimeUnit.SECONDS);
                nioFiber.dispose();
            }
        };
    }


    public static class LongSerializer implements Serializer<Long, Long> {

        @Override
        public ObjectByteWriter<Long> getWriter() {
            return new ObjectByteWriter<Long>() {
                ByteBuffer buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.BIG_ENDIAN);

                @Override
                public void write(String topic, Long msg, ByteMessageWriter writer) {
                    buffer.clear();
                    buffer.putLong(msg);
                    buffer.flip();
                    writer.writeObjectAsBytes(buffer);
                }
            };
        }

        @Override
        public ObjectByteReader<Long> getReader() {
            return (topic, bb, length) -> bb.getLong();
        }
    }

}

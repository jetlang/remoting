package org.jetlang.remote.example;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.core.TcpClientNioFiber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class TcpClientNioFiberMain {

    public static void main(String[] args) throws InterruptedException {
        NioFiberImpl fiber = new NioFiberImpl();
        fiber.start();
        TcpClientNioFiber tcp = new TcpClientNioFiber(fiber);

        InetSocketAddress socketAddress = new InetSocketAddress("localhost", 8080);
        TcpClientNioFiber.ChannelFactory factory = new TcpClientNioFiber.ChannelFactory() {

            public SocketAddress getRemoteAddress() {
                return socketAddress;
            }

            @Override
            public SocketChannel createNewSocketChannel() {
                System.out.println("Creating a new socket");
                try {
                    SocketChannel open = SocketChannel.open();
                    open.configureBlocking(false);
                    return open;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer) {
                System.out.println("chan = " + chan);
                writer.write("Hello".getBytes(StandardCharsets.UTF_8));
                return new TcpClientNioFiber.ConnectedClient() {
                    @Override
                    public boolean read(SocketChannel chan) {
                        int read = 0;
                        try {
                            ByteBuffer allocate = ByteBuffer.allocate(1024);
                            read = chan.read(allocate);
                            if (read == -1) {
                                return false;
                            }
                            allocate.flip();
                            byte[] str = new byte[read];
                            allocate.get(str);
                            System.out.println("read: " + new String(str));
                        } catch (IOException e) {
                            e.printStackTrace();
                            return false;
                        }
                        System.out.println("read = " + read);
                        return true;
                    }

                    @Override
                    public boolean onDisconnect() {
                        System.out.println("TcpClientNioFiberMain.onDisconnect");
                        return true;
                    }
                };
            }
        };
        Disposable close = tcp.connect(factory, 5, TimeUnit.SECONDS);

        Thread.sleep(60 * 1000);
        close.dispose();
        System.out.println("Disposed");
        Thread.sleep(100);
        System.out.println("Stopping Fiber");
        fiber.dispose();
        Thread.sleep(250);
        System.out.println("Done");
    }
}

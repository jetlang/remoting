package org.jetlang.remote.example;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.TcpClientNioConfig;
import org.jetlang.remote.core.TcpClientNioFiber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TcpClientNioFiberMain {

    public static void main(String[] args) throws InterruptedException {
        NioFiberImpl fiber = new NioFiberImpl();
        fiber.start();
        TcpClientNioFiber tcp = new TcpClientNioFiber(fiber);

        Supplier<SocketAddress> socketAddress = () -> new InetSocketAddress("localhost", 8080);
        TcpClientNioConfig.ClientFactory clientFact = (chan, nioFiber, writer) -> {
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
                public void onDisconnect() {
                    System.out.println("TcpClientNioFiberMain.onDisconnect");
                }
            };
        };
        TcpClientNioConfig factory = new TcpClientNioConfig.Default(new ErrorHandler.SysOut(),
                socketAddress, clientFact, 5, 10, TimeUnit.SECONDS){
            @Override
            public boolean onConnectTimeout(SocketChannel chan) {
                System.out.println("ConnectTimeout: " + chan);
                return true;
            }
        };
        Disposable close = tcp.connect(factory);

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

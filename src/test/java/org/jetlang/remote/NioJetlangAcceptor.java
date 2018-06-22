package org.jetlang.remote;

import org.jetlang.fibers.NioBatchExecutorImpl;
import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.List;

public class NioJetlangAcceptor {

    public static void main(String[] args) throws IOException, InterruptedException {
        final List<NioChannelHandler> objects = Collections.emptyList();
        NioFiberImpl.WriteFailure writeFailure = new NioFiberImpl.WriteFailure() {
            @Override
            public <T extends SelectableChannel & WritableByteChannel> void onFailure(IOException e, T t, ByteBuffer byteBuffer) {
                e.printStackTrace();
            }
        };
        final NioFiberImpl fiber = new NioFiberImpl(new NioBatchExecutorImpl(), objects, "nioThread", false, writeFailure, new NioFiberImpl.NoOpBuffer());
        final NioAcceptorHandler acceptorEnd = NioAcceptorHandler.create(8089,
                (acceptorFiber, controls, key, channel) -> {
                    controls.addHandler(new Client(channel, fiber));
                }, () -> System.out.println("AcceptorEnd")
        );
        fiber.addHandler(acceptorEnd);
        fiber.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                super.run();
                System.out.println("Disposing");
                fiber.dispose();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {

                }
                System.out.println("Disposed");

            }
        });
        System.out.println("Done");
    }

    private static class Client implements NioChannelHandler {
        private final SocketChannel accept;
        private final NioFiberImpl nioWriter;
        private final byte[] data = new byte[1024 * 1024];
        final ByteBuffer allocate = ByteBuffer.wrap(data);
        private String remoteAddress;

        public Client(SocketChannel accept, NioFiberImpl nioWriter) {
            this.accept = accept;
            this.nioWriter = nioWriter;
            try {
                accept.socket().setSendBufferSize(8);
                accept.socket().setReceiveBufferSize(1024 * 1024);
                accept.socket().setTcpNoDelay(true);
                System.out.println(accept.socket().getSendBufferSize());
                this.remoteAddress = accept.getRemoteAddress().toString();
            } catch (IOException e) {
                this.remoteAddress = "unknown";
            }
            System.out.println("Connected: " + remoteAddress);
        }

        @Override
        public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
            try {
                int read = accept.read(allocate);
                if (read == -1) {
                    return Result.CloseSocket;
                }
                System.out.println("read = " + read);
                allocate.flip();
                controls.write(accept, allocate);
                allocate.clear();
                return Result.Continue;
            } catch (IOException e) {
                return Result.CloseSocket;
            }
        }

        @Override
        public SelectableChannel getChannel() {
            return accept;
        }

        @Override
        public int getInterestSet() {
            return SelectionKey.OP_READ;
        }

        @Override
        public void onEnd() {
            try {
                accept.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Client end: " + remoteAddress);
        }

        @Override
        public void onSelectorEnd() {
            try {
                accept.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("SelectorEnd ClientEnd: " + remoteAddress);
        }
    }
}

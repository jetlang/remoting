package org.jetlang.remote;

import org.jetlang.fibers.NioBatchExecutorImpl;
import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;
import org.jetlang.remote.acceptor.NioClientHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collections;

public class NioJetlangClient {

    private static byte val = (byte) '1';


    public static void main(String[] args) throws IOException {
        NioFiberImpl fiber = new NioFiberImpl(new NioBatchExecutorImpl(), Collections.emptyList(), "client", false, new NioFiberImpl.NoOpWriteFailure(), new NioFiberImpl.NoOpBuffer());
        fiber.start();
        NioClientHandler handler = NioClientHandler.create("localhost", 8089, new NioClientHandler.Reader() {
            byte[] bytes = new byte[1024 * 1024];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int totalRead;

            @Override
            public NioChannelHandler.Result onRead(NioFiber nioFiber, NioControls controls, SelectionKey key, SocketChannel channel) {
                try {
                    int read = channel.read(buffer);
                    if (read == -1) {
                        System.out.println("endread = " + read);
                        return NioChannelHandler.Result.CloseSocket;
                    }
                    totalRead += read;
                    buffer.flip();
                    System.out.println("read = " + read + " " + totalRead);
                    for (int i = 0; i < read; i++) {
                        if (val != buffer.get()) {
                            System.out.println(val);
                        }
                    }
                    //System.out.println(new String(bytes, 0, read));
                    buffer.clear();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return NioChannelHandler.Result.Continue;
            }
        });
        fiber.addHandler(handler);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        for (String line = reader.readLine(); line.length() > 0; line = reader.readLine()) {
            int length = Integer.parseInt(line);
            byte[] toSend = new byte[length];
            Arrays.fill(toSend, val);
            fiber.execute((controls) -> controls.write(handler.getSocket(), ByteBuffer.wrap(toSend)));
        }
        fiber.dispose();
    }
}

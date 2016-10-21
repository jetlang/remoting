package org.jetlang.web;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class RoundRobinClientFactory implements NioAcceptorHandler.ClientFactory {

    private final List<NioAcceptorHandler.ClientFactory> clients = new ArrayList<>();
    private int position;


    public void add(NioAcceptorHandler.ClientFactory fact) {
        clients.add(fact);
    }


    @Override
    public void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel) {
        int client = position++ % clients.size();
        clients.get(client).onAccept(fiber, controls, key, channel);
    }
}

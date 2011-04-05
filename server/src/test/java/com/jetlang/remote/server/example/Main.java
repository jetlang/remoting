package com.jetlang.remote.server.example;

import com.jetlang.remote.server.Acceptor;
import com.jetlang.remote.server.JetlangClientHandler;
import com.jetlang.remote.server.JetlangSessionChannels;

import java.io.IOException;
import java.net.ServerSocket;

public class Main {

    public static void main(String[] args) throws IOException {


        JetlangSessionChannels sessions = new JetlangSessionChannels();
        JetlangClientHandler handler = new JetlangClientHandler(null, sessions);

        Acceptor acceptor = new Acceptor(
                new ServerSocket(8081),
                new Acceptor.ErrorHandler.SysOut(),
                handler);

    }
}

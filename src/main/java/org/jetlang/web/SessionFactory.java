package org.jetlang.web;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public interface SessionFactory<T> {

    static SessionFactory<Map<String, Object>> mapPerClient() {
        return new SessionFactory<Map<String, Object>>() {
            @Override
            public Map<String, Object> create(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequest headers) {
                return new HashMap<>();
            }

            @Override
            public void onClose(Map<String, Object> session) {

            }
        };
    }

    static SessionFactory<Void> none() {
        return new SessionFactory<Void>() {
            @Override
            public Void create(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequest headers) {
                return null;
            }

            @Override
            public void onClose(Void session) {

            }
        };
    }

    T create(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequest headers);

    void onClose(T session);
}

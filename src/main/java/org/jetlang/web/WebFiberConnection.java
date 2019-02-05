package org.jetlang.web;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

public class WebFiberConnection implements WebSocketConnection {

    private final Fiber fiber;
    private final WebSocketConnection connection;

    public WebFiberConnection(Fiber fiber, WebSocketConnection connection) {
        this.fiber = fiber;
        this.connection = connection;
    }

    @Override
    public HttpRequest getRequest() {
        return connection.getRequest();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return connection.getRemoteAddress();
    }

    @Override
    public SendResult send(String msg) {
        return connection.send(msg);
    }

    @Override
    public SendResult sendText(byte[] bytes, int offset, int length) {
        return connection.sendText(bytes, offset, length);
    }

    @Override
    public SendResult sendPong(byte[] bytes, int offset, int length) {
        return connection.sendPong(bytes, offset, length);
    }

    @Override
    public SendResult sendPing(byte[] bytes, int offset, int length) {
        return connection.sendPing(bytes, offset, length);
    }

    @Override
    public SendResult sendBinary(byte[] buffer, int offset, int length) {
        return connection.sendBinary(buffer, offset, length);
    }

    @Override
    public void close() {
        connection.close();
    }

    @Override
    public void add(Disposable disposable) {
        fiber.add(disposable);
    }

    @Override
    public boolean remove(Disposable disposable) {
        return fiber.remove(disposable);
    }

    @Override
    public int size() {
        return fiber.size();
    }

    @Override
    public void execute(Runnable command) {
        fiber.execute(command);
    }

    @Override
    public Disposable schedule(Runnable command, long delay, TimeUnit unit) {
        return fiber.schedule(command, delay, unit);
    }

    @Override
    public Disposable scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return fiber.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public Disposable scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return fiber.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public void dispose() {
        fiber.dispose();
        connection.dispose();
    }
}

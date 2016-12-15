package org.jetlang.web;

import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;

import java.util.Arrays;
import java.util.function.Supplier;

public interface SessionDispatcherFactory<S> {

    SessionDispatcher<S> createOnNewSession(S session, HttpRequest headers);

    class OnReadThreadDispatcher<S> implements SessionDispatcherFactory<S> {

        @Override
        public SessionDispatcher<S> createOnNewSession(S session, HttpRequest headers) {
            return new OnReadThread<>();
        }
    }

    class OnReadThread<S> implements SessionDispatcher<S> {

        @Override
        public <T> WebSocketHandler<S, T> createOnNewSession(WebSocketHandler<S, T> handler, HttpRequest headers, S sessionState) {
            return handler;
        }

        @Override
        public NioReader.State dispatch(HttpHandler<S> handler, HttpRequest headers, HeaderReader<S> headerReader, NioWriter writer, S sessionState) {
            handler.handle(headerReader.getReadFiber(), headers, headerReader.getHttpResponseWriter(), sessionState);
            return headerReader.start();
        }

        @Override
        public void onClose(S session) {
        }
    }

    interface SessionDispatcher<S> {
        <T> WebSocketHandler<S, T> createOnNewSession(WebSocketHandler<S, T> handler, HttpRequest headers, S sessionState);

        NioReader.State dispatch(HttpHandler<S> handler, HttpRequest headers, HeaderReader<S> headerReader, NioWriter writer, S sessionState);

        void onClose(S session);
    }

    class FiberSessionFactory<S> implements SessionDispatcherFactory<S> {
        private final Supplier<Fiber> fiberFactory;

        public FiberSessionFactory(Supplier<Fiber> fiberFactory) {
            this.fiberFactory = fiberFactory;
        }

        public FiberSessionFactory(PoolFiberFactory poolFiberFactory) {
            this(poolFiberFactory::create);
        }

        @Override
        public SessionDispatcher<S> createOnNewSession(S session, HttpRequest headers) {
            Fiber fiber = fiberFactory.get();
            fiber.start();
            return new FiberSession<S>(fiber);
        }
    }

    class FiberSession<S> implements SessionDispatcher<S> {

        private final Fiber fiber;
        private boolean isWebsocket;

        public FiberSession(Fiber fiber) {
            this.fiber = fiber;
        }

        @Override
        public <T> WebSocketHandler<S, T> createOnNewSession(WebSocketHandler<S, T> handler, HttpRequest headers, S sessionState) {
            isWebsocket = true;
            return new WebSocketHandler<S, T>() {
                private WebFiberConnection fiberConn;
                private T threadState;

                @Override
                public T onOpen(WebSocketConnection connection, HttpRequest headers, S sessionState) {
                    fiberConn = new WebFiberConnection(fiber, connection);
                    fiber.execute(() -> {
                        threadState = handler.onOpen(fiberConn, headers, sessionState);
                    });
                    return null;
                }

                @Override
                public void onPing(WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
                    final byte[] copy = Arrays.copyOf(result, size);
                    fiber.execute(() -> {
                        handler.onPing(fiberConn, threadState, copy, size, charset);
                    });
                }

                @Override
                public void onPong(WebSocketConnection connection, T state, byte[] result, int size) {
                    final byte[] copy = Arrays.copyOf(result, size);
                    fiber.execute(() -> {
                        handler.onPong(fiberConn, threadState, copy, size);
                    });
                }

                @Override
                public void onMessage(WebSocketConnection connection, T state, String msg) {
                    fiber.execute(() -> {
                        handler.onMessage(fiberConn, threadState, msg);
                    });
                }

                @Override
                public void onClose(WebSocketConnection connection, T state) {
                    fiber.execute(() -> {
                        handler.onClose(fiberConn, threadState);
                        fiber.dispose();
                    });
                }

                @Override
                public void onError(WebSocketConnection connection, T state, String msg) {
                    fiber.execute(() -> {
                        handler.onError(fiberConn, threadState, msg);
                    });
                }

                @Override
                public void onException(WebSocketConnection connection, T state, Exception failed) {
                    fiber.execute(() -> {
                        handler.onException(fiberConn, threadState, failed);
                    });
                }

                @Override
                public void onBinaryMessage(WebSocketConnection connection, T state, byte[] result, int size) {
                    final byte[] copy = Arrays.copyOf(result, size);
                    fiber.execute(() -> {
                        handler.onBinaryMessage(fiberConn, threadState, copy, size);
                    });
                }
            };
        }

        @Override
        public NioReader.State dispatch(HttpHandler<S> handler, HttpRequest headers, HeaderReader<S> headerReader, NioWriter writer, S sessionState) {
            fiber.execute(() -> {
                handler.handle(headerReader.getReadFiber(), headers, headerReader.getHttpResponseWriter(), sessionState);
            });
            return headerReader.start();
        }

        @Override
        public void onClose(S session) {
            if (!isWebsocket) {
                fiber.dispose();
            }
        }
    }
}

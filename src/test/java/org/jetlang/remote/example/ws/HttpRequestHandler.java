package org.jetlang.remote.example.ws;

import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Map;

public interface HttpRequestHandler {
    NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer);

    default void configureNewClient(SocketChannel channel) {
        try {
            channel.socket().setTcpNoDelay(true);
        } catch (SocketException e) {

        }
    }

    public static class Default implements HttpRequestHandler {
        private Map<String, Handler> handlerMap;

        public Default(Map<String, Handler> handlerMap) {
            this.handlerMap = handlerMap;
        }

        @Override
        public NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer) {
            Handler h = handlerMap.get(headers.getRequestUri());
            if (h != null) {
                return h.start(headers, reader, writer);
            } else {
                TextPlainResponse response = new TextPlainResponse(404, "Not Found", headers.getRequestUri() + " Not Found", HeaderReader.ascii);
                writer.send(response.getByteBuffer());
                return reader.start();
            }
        }
    }
}

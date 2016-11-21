package org.jetlang.web;

import java.util.Map;

public interface HttpRequestHandler<T> {
    NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer, T sessionState);

    class Default<T> implements HttpRequestHandler<T> {
        private Map<String, Handler> handlerMap;

        public Default(Map<String, Handler> handlerMap) {
            this.handlerMap = handlerMap;
        }

        @Override
        public NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer, T sessionState) {
            Handler h = handlerMap.get(headers.getRequestUri());
            if (h != null) {
                return h.start(headers, reader, writer, sessionState);
            } else {
                TextPlainResponse response = new TextPlainResponse(404, "Not Found", headers.getRequestUri() + " Not Found", HeaderReader.ascii);
                writer.send(response.getByteBuffer());
                return reader.start();
            }
        }
    }
}

package org.jetlang.web;

import java.util.Map;

public interface HttpRequestHandler {
    NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer);

    class Default implements HttpRequestHandler {
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

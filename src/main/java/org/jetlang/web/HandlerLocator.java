package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface HandlerLocator<T> {

    Handler<T> find(HttpRequest headers, T sessionState);

    class List<T> implements HandlerLocator<T> {
        private final ArrayList<HandlerLocator<T>> events = new ArrayList<>();

        public void add(PathMatcher<T> matcher, Handler<T> handler) {
            add(new PathLocator<T>(matcher, handler));
        }

        public void add(HandlerLocator<T> locator) {
            events.add(locator);
        }

        @Override
        public Handler<T> find(HttpRequest headers, T sessionState) {
            for (HandlerLocator<T> event : events) {
                Handler<T> h = event.find(headers, sessionState);
                if (h != null) {
                    return h;
                }
            }
            return null;
        }
    }

    static Map<String, String> createContentTypeMap() {
        Map<String, String> map = new HashMap<>();
        map.put("html", "text/html");
        map.put("htm", "text/html");
        return map;
    }

    class ResourcesDirectory<T> implements HandlerLocator<T> {

        private final HttpSecurity<T> security;
        private final Map<String, String> fileExtensionToContentType;
        private final Path path;

        public ResourcesDirectory(Path dir, HttpSecurity<T> security, Map<String, String> fileExtensionToContentType) {
            path = dir;
            this.security = security;
            this.fileExtensionToContentType = fileExtensionToContentType;
        }

        public ResourcesDirectory(Path dir) {
            this(dir, HttpSecurity.none(), createContentTypeMap());
        }

        @Override
        public Handler<T> find(HttpRequest headers, T sessionState) {
            int i = headers.getRequestUri().lastIndexOf('.');
            if (i == -1) {
                return null;
            }
            String contentType = fileExtensionToContentType.get(headers.getRequestUri().substring(i + 1));
            if (contentType == null) {
                return null;
            }
            String requestUri = headers.getRequestUri().startsWith("/") ? headers.getRequestUri().substring(1) : headers.getRequestUri();
            Path resource = path.resolve(requestUri);
            if (Files.exists(resource)) {
                return new AuthHttpHandler<T>(new HttpHandler<T>() {
                    @Override
                    public void handle(NioFiber readFiber, HttpRequest headers, HttpResponseWriter writer, T sessionState) {
                        try {
                            byte[] bytes = Files.readAllBytes(resource);
                            writer.sendResponse(200, "OK", contentType, bytes);
                        } catch (IOException e) {
                            writer.sendResponse(404, "Not Found", "text/plain", e.getMessage(), HeaderReader.ascii);
                        }
                    }
                }, security);
            }
            return null;
        }
    }

}

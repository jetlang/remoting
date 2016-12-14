package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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

    static Map<String, String> createDefaultMimeTypeMap() {
        Map<String, String> map = new HashMap<>();
        map.put("html", "text/html");
        map.put("htm", "text/html");
        map.put("txt", "text/plain");
        map.put("css", "text/css");
        map.put("csv", "text/csv");
        map.put("xml", "text/xml");
        map.put("js", "text/javascript");
        map.put("xhtml", "application/xhtml+xml");
        map.put("json", "application/json");
        map.put("pdf", "application/pdf");
        map.put("zip", "application/zip");
        map.put("tar", "application/x-tar");
        map.put("gif", "image/gif");
        map.put("jpeg", "image/jpeg");
        map.put("jpg", "image/jpeg");
        map.put("tiff", "image/tiff");
        map.put("tif", "image/tiff");
        map.put("png", "image/png");
        map.put("svg", "image/svg+xml");
        map.put("ico", "image/vnd.microsoft.icon");
        return map;
    }

    class ResourcesDirectory<T> implements HandlerLocator<T> {

        public static final Map<String, String> defaultMimeTypes = Collections.unmodifiableMap(createDefaultMimeTypeMap());
        private final HttpSecurity<T> security;
        private final Map<String, String> fileExtensionToContentType;
        private final Path path;

        public ResourcesDirectory(Path dir, HttpSecurity<T> security, Map<String, String> fileExtensionToContentType) {
            path = dir;
            this.security = security;
            this.fileExtensionToContentType = fileExtensionToContentType;
        }

        public ResourcesDirectory(Path dir) {
            this(dir, HttpSecurity.none(), defaultMimeTypes);
        }

        @Override
        public Handler<T> find(HttpRequest headers, T sessionState) {
            String path = headers.getPath();
            if (path.endsWith("/") || path.isEmpty()) {
                path += "index.html";
            }
            int i = path.lastIndexOf('.');
            if (i == -1) {
                return null;
            }
            String contentType = fileExtensionToContentType.get(path.substring(i + 1));
            if (contentType == null) {
                return null;
            }
            String requestUri = path.startsWith("/") ? path.substring(1) : path;
            Path resource = this.path.resolve(requestUri);
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

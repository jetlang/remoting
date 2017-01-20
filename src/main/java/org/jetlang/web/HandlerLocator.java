package org.jetlang.web;

import org.jetlang.fibers.Fiber;

import java.io.IOException;
import java.nio.charset.Charset;
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

    interface MimeType {

        String getContentType();

        Charset getCharset();
    }

    class Text implements MimeType {

        private final String type;
        private final Charset charset;

        public Text(String type, Charset charset) {
            this.type = type;
            this.charset = charset;
        }

        @Override
        public String getContentType() {
            return type;
        }

        @Override
        public Charset getCharset() {
            return charset;
        }
    }

    class Binary implements MimeType {
        private final String type;

        public Binary(String type) {
            this.type = type;
        }

        @Override
        public String getContentType() {
            return type;
        }

        @Override
        public Charset getCharset() {
            return null;
        }
    }

    class Utf8Text extends Text {
        public static final Charset utf8 = Charset.forName("UTF-8");

        public Utf8Text(String type) {
            super(type, utf8);
        }

    }

    static Map<String, MimeType> createDefaultMimeTypeMap() {
        Map<String, MimeType> map = new HashMap<>();
        map.put("html", new Utf8Text("text/html"));
        map.put("htm", new Utf8Text("text/html"));
        map.put("txt", new Utf8Text("text/plain"));
        map.put("css", new Utf8Text("text/css"));
        map.put("csv", new Utf8Text("text/csv"));
        map.put("xml", new Utf8Text("text/xml"));
        map.put("js", new Utf8Text("text/javascript"));
        map.put("xhtml", new Utf8Text("application/xhtml+xml"));
        map.put("json", new Utf8Text("application/json"));
        map.put("pdf", new Binary("application/pdf"));
        map.put("zip", new Binary("application/zip"));
        map.put("tar", new Binary("application/x-tar"));
        map.put("gif", new Binary("image/gif"));
        map.put("jpeg", new Binary("image/jpeg"));
        map.put("jpg", new Binary("image/jpeg"));
        map.put("tiff", new Binary("image/tiff"));
        map.put("tif", new Binary("image/tiff"));
        map.put("png", new Binary("image/png"));
        map.put("svg", new Binary("image/svg+xml"));
        map.put("ico", new Binary("image/vnd.microsoft.icon"));
        return map;
    }

    class ResourcesDirectory<T> implements HandlerLocator<T> {

        public static final Map<String, MimeType> defaultMimeTypes = Collections.unmodifiableMap(createDefaultMimeTypeMap());
        private final HttpSecurity<T> security;
        private final Map<String, MimeType> fileExtensionToContentType;
        private final Path path;

        public ResourcesDirectory(Path dir, HttpSecurity<T> security, Map<String, MimeType> fileExtensionToContentType) {
            //remove relative paths and make absolute
            path = getReal(dir);
            this.security = security;
            this.fileExtensionToContentType = fileExtensionToContentType;
        }

        private static Path getReal(Path dir) {
            try {
                return dir.toRealPath();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public ResourcesDirectory(Path dir) {
            this(dir, HttpSecurity.none(), defaultMimeTypes);
        }

        @Override
        public Handler<T> find(HttpRequest headers, T sessionState) {
            final String reqPath = headers.getPath();
            final String requestUri = reqPath.startsWith("/") ? reqPath.substring(1) : reqPath;

            //must normalize and check if the resource is a subdirectory to prevent a path traversal attack
            Path resource = this.path.resolve(requestUri).normalize();
            if (Files.isDirectory(resource)) {
                resource = resource.resolve("index.html");
            }
            if (resource.startsWith(this.path) && Files.exists(resource)) {
                String resourceToString = resource.toString();
                int i = resourceToString.lastIndexOf('.');
                if (i == -1) {
                    return null;
                }
                MimeType contentType = fileExtensionToContentType.get(resourceToString.substring(i + 1));
                if (contentType == null) {
                    return null;
                }
                Path finalResource = resource;
                return new AuthHttpHandler<T>(new HttpHandler<T>() {
                    @Override
                    public void handle(Fiber dispatchFiber, HttpRequest headers, HttpResponse writer, T sessionState) {
                        try {
                            byte[] bytes = Files.readAllBytes(finalResource);
                            writer.sendResponse(200, "OK", contentType.getContentType(), bytes, contentType.getCharset());
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

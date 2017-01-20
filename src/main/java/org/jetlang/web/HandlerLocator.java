package org.jetlang.web;

import org.jetlang.fibers.Fiber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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

    class ResourcesDirectory<T> implements HandlerLocator<T> {

        public static final Map<String, MimeType> defaultMimeTypes = Collections.unmodifiableMap(MimeType.createDefaultMimeTypeMap());
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

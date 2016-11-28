package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import java.nio.charset.Charset;
import java.nio.file.Path;

public class StaticHtml<T> implements HttpHandler<T> {
    private final Path resource;
    private final Charset charset;

    public StaticHtml(Path resource) {
        this(resource, Charset.defaultCharset());
    }

    public StaticHtml(Path resource, Charset charset) {
        this.resource = resource;
        this.charset = charset;
    }

    @Override
    public void handle(NioFiber readFiber, HttpRequest headers, HttpResponseWriter writer, T sessionState) {
        writer.sendResponse(200, "OK", "text/html", resource, charset);
    }
}

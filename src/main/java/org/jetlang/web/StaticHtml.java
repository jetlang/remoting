package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import java.nio.file.Path;

public class StaticHtml<T> implements HttpHandler<T> {
    private final Path resource;

    public StaticHtml(Path resource) {
        this.resource = resource;
    }

    @Override
    public void handle(NioFiber readFiber, HttpRequest headers, HttpResponse writer, T sessionState) {
        writer.sendResponse(200, "OK", "text/html", resource);
    }
}

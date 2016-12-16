package org.jetlang.web;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class HttpRequest {

    private static final byte[] empty = new byte[0];
    private final HeaderList headers = new HeaderList();
    String method;
    URI requestUri;
    String protocolVersion;
    int contentLength;
    byte[] content = empty;

    public HttpRequest(String method, String uri, String protocolVersion) {
        this.method = method;
        this.requestUri = URI.create(uri);
        this.protocolVersion = protocolVersion;
    }

    public HttpRequest() {

    }

    public String get(String key) {
        return headers.get(key);
    }

    public byte[] getContent() {
        return content;
    }

    public HeaderList getHeaders() {
        return headers;
    }

    public String getMethod() {
        return method;
    }

    public URI getRequestUri() {
        return requestUri;
    }

    public String getPath() {
        return requestUri.getPath();
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public String toString() {
        return "HttpRequest{" +
                "headers=" + headers +
                ", method='" + method + '\'' +
                ", requestUri='" + requestUri + '\'' +
                ", protocolVersion='" + protocolVersion + '\'' +
                '}';
    }

    public void add(String name, String value) {
        headers.add(name, value);
    }

    public ByteBuffer toByteBuffer(Charset charset) {
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(" ").append(requestUri).append(" ").append(protocolVersion).append("\r\n");
        headers.appendTo(builder);
        String result = builder.append("\r\n").toString();
        return ByteBuffer.wrap(result.getBytes(charset));
    }
}

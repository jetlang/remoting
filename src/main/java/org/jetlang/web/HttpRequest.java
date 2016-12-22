package org.jetlang.web;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class HttpRequest {

    private static final byte[] empty = new byte[0];
    private final KeyValueList headers = new KeyValueList(false);
    String method;
    private URI requestUri;
    String protocolVersion;
    int contentLength;
    byte[] content = empty;
    private KeyValueList queryParams = KeyValueList.EMPTY;

    public HttpRequest(String method, String uri, String protocolVersion) {
        this.method = method;
        this.setRequestUri(URI.create(uri));
        this.protocolVersion = protocolVersion;
    }

    public HttpRequest() {

    }

    public KeyValueList getQueryParams() {
        return queryParams;
    }

    public void setRequestUri(URI requestUri) {
        this.requestUri = requestUri;
        this.queryParams = splitQuery(requestUri);
    }

    public static KeyValueList splitQuery(URI url) {
        if (url.getQuery() == null || url.getQuery().isEmpty()) {
            return KeyValueList.EMPTY;
        }
        final String[] pairs = url.getQuery().split("&");
        final KeyValueList query_pairs = new KeyValueList(pairs.length, false);
        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final String key = idx > 0 ? decode(pair.substring(0, idx)) : pair;
            final String value = idx > 0 && pair.length() > idx + 1 ? decode(pair.substring(idx + 1)) : null;
            query_pairs.add(key, value);
        }
        return query_pairs;
    }

    private static String decode(String substring) {
        try {
            return URLDecoder.decode(substring, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    public String get(String key) {
        return headers.get(key);
    }

    public byte[] getContent() {
        return content;
    }

    public KeyValueList getHeaders() {
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

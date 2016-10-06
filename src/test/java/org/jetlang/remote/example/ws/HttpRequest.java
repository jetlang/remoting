package org.jetlang.remote.example.ws;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {

    private final Map<String, String> headers = new HashMap<>();
    String method;
    String requestUri;
    String protocolVersion;

    public String get(String key) {
        return headers.get(key);
    }

    void put(String name, String value) {
        headers.put(name, value);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getMethod() {
        return method;
    }

    public String getRequestUri() {
        return requestUri;
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
}

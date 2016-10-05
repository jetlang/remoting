package org.jetlang.remote.example.ws;

import java.util.HashMap;
import java.util.Map;

public class HttpHeaders {

    private final Map<String, String> headers = new HashMap<>();
    String method;
    String requestUri;
    String protocolVersion;

    public String get(String key) {
        return headers.get(key);
    }

    public void put(String name, String value) {
        headers.put(name, value);
    }
}

package org.jetlang.remote.example.ws;

import java.util.ArrayList;
import java.util.List;

public class HttpRequest {

    private final List<Header> headers = new ArrayList<>();
    String method;
    String requestUri;
    String protocolVersion;

    public String get(String key) {
        for (int i = 0; i < headers.size(); i++) {
            Header header = headers.get(i);
            if (header.name.equals(key)) {
                return header.value;
            }
        }
        return null;
    }

    void put(String name, String value) {
        headers.add(new Header(name, value));
    }

    public List<Header> getHeaders() {
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

    public static class Header {

        private final String name;
        private final String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + '=' + value;
        }
    }
}

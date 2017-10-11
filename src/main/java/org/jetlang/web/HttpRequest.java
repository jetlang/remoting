package org.jetlang.web;

import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.StringTokenizer;

public class HttpRequest {

    public static final Charset defaultBodyCharset = Charset.forName("ISO-8859-1");

    private static final byte[] empty = new byte[0];
    private final KeyValueList headers = new KeyValueList(false);
    private final SocketAddress socketAddress;
    String method;
    private URI requestUri;
    String protocolVersion;
    int contentLength;
    byte[] content = empty;
    private KeyValueList queryParams = KeyValueList.EMPTY;

    public HttpRequest(String method, String uri, String protocolVersion, SocketAddress remoteAddress) {
        this.method = method;
        this.setRequestUri(URI.create(uri));
        this.protocolVersion = protocolVersion;
        this.socketAddress = remoteAddress;
    }

    public HttpRequest(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    public SocketAddress getRemoteAddress() {
        return socketAddress;
    }

    public KeyValueList getQueryParams() {
        return queryParams;
    }

    public void setRequestUri(URI requestUri) {
        this.requestUri = requestUri;
        this.queryParams = splitQuery(requestUri);
    }

    public static KeyValueList splitQuery(URI url) {
        String query = url.getRawQuery();
        return KeyValueList.parseUrlEncoded(query, false);
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

    /**
     * @return charset found in request or default body charset if one isn't present
     */
    public Charset getContentCharset(boolean failOnUnsupported) {
        String s = headers.get("Content-Type");
        if (s != null) {
            StringTokenizer content = new StringTokenizer(s, ";");
            while (content.hasMoreElements()) {
                String candidate = content.nextToken().trim();
                int idx = candidate.indexOf('=');
                if (idx == 7 && candidate.length() > 8) {
                    String name = candidate.substring(0, 7);
                    if (name.equalsIgnoreCase("charset")) {
                        String value = candidate.substring(8).trim();
                        try {
                            return Charset.forName(value);
                        } catch (UnsupportedCharsetException unsupported) {
                            if (failOnUnsupported) {
                                throw unsupported;
                            }
                        }
                    }
                }
            }
        }
        return defaultBodyCharset;
    }

    public String getContentAsString(boolean failOnUnsupportedEncoding) {
        return new String(content, getContentCharset(failOnUnsupportedEncoding));
    }
}

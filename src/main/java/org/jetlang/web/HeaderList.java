package org.jetlang.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public class HeaderList implements Iterable<HeaderList.Header> {

    private final List<Header> headers = new ArrayList<>();

    public String get(String key) {
        for (int i = 0; i < headers.size(); i++) {
            Header header = headers.get(i);
            if (header.name.equals(key)) {
                return header.value;
            }
        }
        return null;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    @Override
    public Iterator<Header> iterator() {
        return headers.iterator();
    }

    @Override
    public void forEach(Consumer<? super Header> action) {
        headers.forEach(action);
    }

    @Override
    public Spliterator<Header> spliterator() {
        return headers.spliterator();
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    public void add(String name, String value) {
        headers.add(new Header(name, value));
    }

    public void appendTo(StringBuilder builder) {
        for (Header header : headers) {
            builder.append(header.name).append(": ").append(header.value).append("\r\n");
        }
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

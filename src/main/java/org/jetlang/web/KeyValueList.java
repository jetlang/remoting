package org.jetlang.web;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.StringTokenizer;
import java.util.function.Consumer;

public class KeyValueList implements Iterable<KeyValueList.Entry> {

    public static final KeyValueList EMPTY = new KeyValueList(Collections.emptyList(), false);
    private final List<Entry> headers;
    private final boolean caseSensitive;

    public KeyValueList(int expectedSize, boolean caseSensitive) {
        this(new ArrayList<Entry>(expectedSize), caseSensitive);
    }

    public KeyValueList(boolean caseSensitive) {
        this(new ArrayList<Entry>(), caseSensitive);
    }

    public KeyValueList(List<Entry> entries, boolean caseSensitive) {
        this.headers = entries;
        this.caseSensitive = caseSensitive;
    }

    public List<Entry> getHeaders() {
        return headers;
    }

    public String get(String key) {
        for (int i = 0; i < headers.size(); i++) {
            Entry header = headers.get(i);
            if (equalsKey(key, header.name)) {
                return header.value;
            }
        }
        return null;
    }

    @Override
    public Iterator<Entry> iterator() {
        return headers.iterator();
    }

    @Override
    public void forEach(Consumer<? super Entry> action) {
        headers.forEach(action);
    }

    @Override
    public Spliterator<Entry> spliterator() {
        return headers.spliterator();
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    public void add(String name, String value) {
        headers.add(new Entry(name, value));
    }

    public void appendTo(StringBuilder builder) {
        for (Entry header : headers) {
            builder.append(header.name).append(": ").append(header.value).append("\r\n");
        }
    }

    public List<String> getAll(String param) {
        List<String> result = new ArrayList<>();
        for (Entry header : headers) {
            if (equalsKey(param, header.name)) {
                result.add(header.value);
            }
        }
        return result;
    }

    private boolean equalsKey(String param, String name) {
        if (caseSensitive) {
            return param.equals(name);
        } else {
            return param.equalsIgnoreCase(name);
        }
    }

    public int size() {
        return headers.size();
    }

    public static KeyValueList parseUrlEncoded(String query, boolean caseSensitive) {
        if (query == null || query.isEmpty()) {
            return EMPTY;
        }
        final StringTokenizer pairs = new StringTokenizer(query, "&");
        final KeyValueList query_pairs = new KeyValueList(pairs.countTokens(), caseSensitive);
        while (pairs.hasMoreTokens()) {
            final String pair = pairs.nextToken();
            final int idx = pair.indexOf('=');
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


    public static class Entry {

        public final String name;
        public final String value;

        public Entry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + '=' + value;
        }
    }
}

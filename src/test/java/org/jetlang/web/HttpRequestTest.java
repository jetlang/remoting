package org.jetlang.web;

import org.junit.Test;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HttpRequestTest {

    @Test
    public void parseUriQueryParams() {
        String uri = "/path?param=1&param=2&other=with+space";
        KeyValueList stringListMap = HttpRequest.splitQuery(URI.create(uri));
        assertEquals(stringListMap.toString(), 3, stringListMap.size());
        List<String> param = stringListMap.getAll("param");
        assertEquals(2, param.size());
        assertEquals("1", param.get(0));
        assertEquals("2", param.get(1));

        List<String> other = stringListMap.getAll("other");
        assertEquals(1, other.size());
        assertEquals("with space", other.get(0));
    }

    @Test
    public void empty() {
        String uri = "/path";
        KeyValueList stringListMap = HttpRequest.splitQuery(URI.create(uri));
        assertEquals(stringListMap.toString(), 0, stringListMap.size());
    }

    @Test
    public void emptyWithQuestionMark() {
        String uri = "/path?";
        KeyValueList stringListMap = HttpRequest.splitQuery(URI.create(uri));
        assertEquals(stringListMap.toString(), 0, stringListMap.size());
    }

}

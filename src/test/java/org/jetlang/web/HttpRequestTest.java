package org.jetlang.web;

import org.junit.Test;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void parseEncoded() {
        String uri = "/keyvaluepair?type=thrift&Key=ENABLD+ZC%2bStuff+BF&Value=";
        URI url = URI.create(uri);
        assertEquals("type=thrift&Key=ENABLD+ZC%2bStuff+BF&Value=", url.getRawQuery());
        KeyValueList stringListMap = HttpRequest.splitQuery(url);
        assertEquals(stringListMap.toString(), 3, stringListMap.size());
        assertEquals("thrift", stringListMap.get("type"));
        assertEquals("ENABLD ZC+Stuff BF", stringListMap.get("Key"));
        assertTrue(stringListMap.contains("Value"));
        assertNull(stringListMap.get("Value"));
    }

    @Test
    public void emptyWithQuestionMark() {
        String uri = "/path?";
        KeyValueList stringListMap = HttpRequest.splitQuery(URI.create(uri));
        assertEquals(stringListMap.toString(), 0, stringListMap.size());
    }

    @Test
    public void testGettingBodyWithEncoding() {
        HttpRequest req = new HttpRequest("POST", "/path", "HTTP/1.1", null);
        req.add("Content-Type", "application/x-www-form-urlencoded ; charset=UTF-8");
        Charset charset = Charset.forName("UTF-8");
        req.content = "Hello".getBytes(charset);
        assertEquals(charset, req.getContentCharset(true));
        assertEquals("Hello", req.getContentAsString(true));
    }


    @Test
    public void testGettingBodyWithBadEncoding() {
        HttpRequest req = new HttpRequest("POST", "/path", "HTTP/1.1", null);
        req.add("Content-Type", "application/x-www-form-urlencoded ; charset=UTF-99");
        try {
            req.getContentCharset(true);
            fail("Should fail");
        } catch (UnsupportedCharsetException expected) {

        }
    }


    @Test
    public void testGettingDefault() {
        HttpRequest req = new HttpRequest("POST", "/path", "HTTP/1.1", null);
        req.add("Content-Type", "application/x-www-form-urlencoded");
        assertEquals(HttpRequest.defaultBodyCharset, req.getContentCharset(true));
    }

    @Test
    public void testGettingDefaultOnError() {
        HttpRequest req = new HttpRequest("POST", "/path", "HTTP/1.1", null);
        req.add("Content-Type", "application/x-www-form-urlencoded ; charset=UTF-99");
        Charset charset = Charset.forName("UTF-8");
        req.content = "Hello".getBytes(charset);
        assertEquals(HttpRequest.defaultBodyCharset, req.getContentCharset(false));
        assertEquals("Hello", req.getContentAsString(false));

    }




}

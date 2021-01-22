package org.jetlang.remote.core;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TopicReaderTest {
    Charset ascii = Charset.forName("ASCII");

    @Test
    public void cache(){
        TopicReader.Cached cache = new TopicReader.Cached(ascii);
        assertEquals("test", create("test", cache));
        String other = create("other", cache);
        String other1 = create("other", cache);
        assertSame(other1, other);
    }

    private String create(String input, TopicReader.Cached cache) {
        ByteBuffer encode = ascii.encode(input);
        return cache.read(encode, encode.limit());
    }
}

package org.jetlang.web;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

public class HttpResponseBuilder {
    private final CharsetEncoder encoder;
    private CharBuffer content = CharBuffer.allocate(1);
    ByteBuffer output = ByteBuffer.allocateDirect(1);

    public HttpResponseBuilder(CharsetEncoder encoder) {
        this.encoder = encoder;
    }

    public HttpResponseBuilder append(String s) {
        if (content.remaining() < s.length()) {
            CharBuffer newBuffer = CharBuffer.allocate(content.position() + s.length());
            content.flip();
            content = newBuffer.put(content);
        }
        content.append(s);
        return this;
    }

    public void encode() {
        content.flip();
        while (true) {
            CoderResult encode = encoder.encode(content, output, true);
            if (encode.isOverflow()) {
                int expected = (int) (encoder.averageBytesPerChar() + 1) * content.limit();
                output = ByteBuffer.allocateDirect(output.capacity() + expected);
                content.position(0);
            } else {
                output.flip();
                return;
            }
        }
    }
}

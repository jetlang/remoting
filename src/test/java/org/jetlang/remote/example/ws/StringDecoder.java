package org.jetlang.remote.example.ws;

import sun.nio.cs.ArrayDecoder;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public interface StringDecoder {

    String decode(byte[] bytes, int offset, int length);

    static StringDecoder create(String name) {
        Charset cs = Charset.forName(name);
        return create(cs);
    }

    static StringDecoder create(Charset cs) {
        CharsetDecoder charsetDecoder = cs.newDecoder();
        if (charsetDecoder instanceof ArrayDecoder) {
            return new StringDecoder() {
                ArrayDecoder decoder = (ArrayDecoder) charsetDecoder;

                @Override
                public String decode(byte[] bytes, int offset, int length) {
                    char[] cs = new char[(int) (length * (double) charsetDecoder.maxCharsPerByte())];
                    int result = decoder.decode(bytes, offset, length, cs);
                    if (result == -1) {
                        return null;
                    }
                    return new String(cs, 0, result);
                }
            };
        } else {
            return new StringDecoder() {
                @Override
                public String decode(byte[] bytes, int offset, int length) {
                    return new String(bytes, offset, length);
                }
            };
        }
    }
}

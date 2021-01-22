package org.jetlang.remote.core;

import sun.nio.cs.ArrayDecoder;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public interface StringDecoder {

    String decode(ByteBuffer bb, int length);
    String decode(byte[] result, int offset, int length);

    static StringDecoder create(String name) {
        Charset cs = Charset.forName(name);
        return create(cs);
    }

    static StringDecoder create(Charset cs) {
        ByteArrayDecoder byteArrayDecoder = createByteArrayDecoder(cs);
        return new StringDecoder() {
            @Override
            public String decode(ByteBuffer bb, int length) {
                byte[] chars = new byte[length];
                bb.get(chars);
                return new String(chars, cs);
            }

            @Override
            public String decode(byte[] result, int i, int size) {
                return byteArrayDecoder.decode(result, i, size);
            }
        };
    }

    interface ByteArrayDecoder {
        String decode(byte[] result, int i, int size);
    }

    static ByteArrayDecoder createByteArrayDecoder(Charset cs){
        CharsetDecoder charsetDecoder = cs.newDecoder();
        if (charsetDecoder instanceof ArrayDecoder) {
            return new ByteArrayDecoder() {
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
            return (bytes, offset, length) -> new String(bytes, offset, length, cs);
        }
    }

}

package org.jetlang.remote.core;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

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
        return (bytes, offset, length) -> new String(bytes, offset, length, cs);
    }

}

package org.jetlang.remote.core;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;

public interface TopicReader {
    String read(ByteBuffer bufferArray, int length);

    class Default implements TopicReader {
        private StringDecoder charset;

        public Default(Charset charset) {
            this.charset = StringDecoder.create(charset);
        }

        @Override
        public String read(ByteBuffer bb, int length) {
            return charset.decode(bb, length);
        }
    }

    class Cached implements TopicReader {
        private final Default charset;
        private final Key searchKey = new Key(new byte[0], 0);
        private final HashMap<Key, String> cache = new HashMap<>();

        public Cached(Charset charset) {
            this.charset = new Default(charset);
        }

        @Override
        public String read(ByteBuffer bb, int length) {
            int origPosition = bb.position();
            searchKey.init(bb, length);
            String result = cache.get(searchKey);
            if (result == null) {
                bb.position(origPosition);
                result = charset.read(bb, length);
                cache.put(searchKey.copy(), result);
            }
            return result;
        }

        private static class Key {

            private int hashCode;
            private int length;
            private byte[] buffer;

            private Key(byte[] bufferArray, int length) {
                this.buffer = Arrays.copyOfRange(bufferArray, 0, length);
                init(length);
            }

            public Key copy() {
                return new Key(buffer, length);
            }

            private void init(ByteBuffer bb, int length) {
                resize(length);
                bb.get(this.buffer, 0, length);
                init( length);
            }

            private void init(int length) {
                this.length = length;
                int hc = 0;
                for (int i = 0; i < length; i++) {
                    hc = 31 * hc + buffer[i];
                }
                this.hashCode = hc;
            }

            private void resize(int length) {
                if (buffer.length < length) {
                    buffer = new byte[length];
                }
            }

            @Override
            public boolean equals(Object other) {
                final Key otherKey = (Key) other;
                final int thisLength = this.length;
                if (thisLength != otherKey.length) {
                    return false;
                }
                final byte[] thisBuffer = this.buffer;
                final byte[] otherBuffer = otherKey.buffer;
                for (int i = 0; i < thisLength; i++) {
                    if (thisBuffer[i] != otherBuffer[i]) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }
    }
}

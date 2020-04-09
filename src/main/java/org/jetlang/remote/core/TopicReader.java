package org.jetlang.remote.core;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;

public interface TopicReader {
    String read(byte[] bufferArray, int offset, int length);

    class Default implements TopicReader {
        private StringDecoder charset;

        public Default(Charset charset) {
            this.charset = StringDecoder.create(charset);
        }

        @Override
        public String read(byte[] bufferArray, int offset, int length) {
            return charset.decode(bufferArray, offset, length);
        }
    }

    class Cached implements TopicReader {
        private final Default charset;
        private final Key searchKey = new Key();
        private final HashMap<Key, String> cache = new HashMap<>();

        public Cached(Charset charset) {
            this.charset = new Default(charset);
        }

        @Override
        public String read(byte[] bufferArray, int offset, int length) {
            searchKey.init(bufferArray, offset, length);
            String result = cache.get(searchKey);
            if(result == null){
                result = charset.read(bufferArray, offset, length);
                cache.put(new Key(bufferArray, offset, length), result);
            }
            return result;
        }

        private static class Key {

            int hashCode;
            int offset;
            int length;
            byte[] buffer;

            public Key(byte[] bufferArray, int offset, int length) {
                byte[] copy = Arrays.copyOfRange(bufferArray, offset, offset + length);
                init(copy, 0, length);
            }
            public Key(){

            }

            public void init(byte[] bufferArray, int offset, int length){
                this.buffer = bufferArray;
                this.offset = offset;
                this.length = length;
                int hc = 0;
                for(int i = 0; i < length; i++){
                    hc += bufferArray[i + offset];
                }
                this.hashCode = hc;
            }

            @Override
            public boolean equals(Object other){
                Key otherKey = (Key)other;
                if(this.length != otherKey.length){
                    return false;
                }
                for(int i = 0; i < length; i++){
                    if(this.buffer[i + this.offset] != otherKey.buffer[i + otherKey.offset]){
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

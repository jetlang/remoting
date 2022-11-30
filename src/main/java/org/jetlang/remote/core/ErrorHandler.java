package org.jetlang.remote.core;

import java.nio.ByteBuffer;

public interface ErrorHandler {
    void onException(Exception e);

    void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed);

    @SuppressWarnings({"CallToPrintStackTrace"})
    class SysOut implements ErrorHandler {
        public void onException(Exception e) {
            e.printStackTrace();
        }

        @Override
        public void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed) {
            failed.printStackTrace();
        }
    }
}

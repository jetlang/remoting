package org.jetlang.remote.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ErrorHandler {
    void onException(Exception e);

    void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed);

    default void onClientDisconnect(IOException disconnect){

    }

    @SuppressWarnings({"CallToPrintStackTrace"})
    class SysOut implements ErrorHandler {
        public void onException(Exception e) {
            e.printStackTrace();
        }

        @Override
        public void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed) {
            failed.printStackTrace();
        }

        @Override
        public void onClientDisconnect(IOException disconnect) {
            disconnect.printStackTrace();
        }
    }
}

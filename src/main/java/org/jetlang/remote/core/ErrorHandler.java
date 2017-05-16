package org.jetlang.remote.core;

public interface ErrorHandler {
    void onException(Exception e);

    void onDataHandlingFailure(String dataTopicVal, Object readObject, Exception failed);

    @SuppressWarnings({"CallToPrintStackTrace"})
    class SysOut implements ErrorHandler {
        public void onException(Exception e) {
            e.printStackTrace();
        }

        @Override
        public void onDataHandlingFailure(String dataTopicVal, Object readObject, Exception failed) {
            failed.printStackTrace();
        }
    }
}

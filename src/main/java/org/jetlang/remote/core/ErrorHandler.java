package org.jetlang.remote.core;

public interface ErrorHandler {
    void onException(Exception e);

    @SuppressWarnings({"CallToPrintStackTrace"})
    class SysOut implements ErrorHandler {
        public void onException(Exception e) {
            e.printStackTrace();
        }
    }
}

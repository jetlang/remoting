package com.jetlang.remote.client;

import java.io.IOException;

/**
 * User: mrettig
 * Date: 4/5/11
 * Time: 4:16 PM
 */
public interface CloseEvent {


    public static class GracefulDisconnect implements CloseEvent {
    }

    public abstract static class IOExceptionEvent implements CloseEvent {
        private final IOException e;

        public IOExceptionEvent(IOException e) {
            this.e = e;
        }

        public IOException getException() {
            return e;
        }
    }

    public static class WriteException extends IOExceptionEvent {
        public WriteException(IOException e) {
            super(e);
        }
    }

    public static class ReadException extends IOExceptionEvent {
        public ReadException(IOException e) {
            super(e);
        }
    }
}

package org.jetlang.remote.client;

import java.io.IOException;

/**
 * User: mrettig
 * Date: 4/5/11
 * Time: 4:16 PM
 */
public interface CloseEvent {


    class GracefulDisconnect implements CloseEvent {
    }

    abstract class IOExceptionEvent implements CloseEvent {
        private final IOException e;

        public IOExceptionEvent(IOException e) {
            this.e = e;
        }

        public IOException getException() {
            return e;
        }

        @Override
        public String toString() {
            return "IOExceptionEvent{" + "e=" + e + '}';
        }
    }

    class WriteException extends IOExceptionEvent {
        public WriteException(IOException e) {
            super(e);
        }
    }

    class ReadException extends IOExceptionEvent {
        public ReadException(IOException e) {
            super(e);
        }
    }
}

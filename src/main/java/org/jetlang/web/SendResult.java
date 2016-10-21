package org.jetlang.web;

public class SendResult {

    public static final SendResult SUCCESS = new SendResult(Type.Success);
    public static final SendResult Closed = new SendResult(Type.Closed);

    private final Type type;

    public SendResult(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        Success, FailedOnError, Buffered, Closed;
    }

    public static class FailedWithError extends SendResult {
        private final Exception failed;

        public FailedWithError(Exception failed) {
            super(Type.FailedOnError);
            this.failed = failed;
        }

        public Exception getFailed() {
            return failed;
        }
    }

    public static class Buffered extends SendResult {
        private final int bufferedBytes;
        private final int totalBufferedInBytes;

        public Buffered(int bufferedBytes, int totalBufferedInBytes) {
            super(Type.Buffered);
            this.bufferedBytes = bufferedBytes;
            this.totalBufferedInBytes = totalBufferedInBytes;
        }

        public int getBufferedBytes() {
            return bufferedBytes;
        }

        public int getTotalBufferedInBytes() {
            return totalBufferedInBytes;
        }
    }
}

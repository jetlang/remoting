package org.jetlang.remote.core;

public interface RawMsgHandler {

    boolean enabled();

    void onRawMsg(RawMsg rawMsg);

    RawMsgHandler NULL_RAW_MSG_HANDLER = new RawMsgHandler() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void onRawMsg(RawMsg rawMsg) {
            throw new UnsupportedOperationException();
        }
    };
}

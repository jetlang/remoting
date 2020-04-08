package org.jetlang.remote.core;

import static org.jetlang.remote.core.RawMsgHandler.NULL_RAW_MSG_HANDLER;

public interface RawMsgHandlerFactory {
    RawMsgHandler rawMsgHandler();

    RawMsgHandlerFactory NULL_RAW_MSG_HANDLER_FACTORY = new RawMsgHandlerFactory() {
        @Override
        public RawMsgHandler rawMsgHandler() {
            return NULL_RAW_MSG_HANDLER;
        }
    };
}


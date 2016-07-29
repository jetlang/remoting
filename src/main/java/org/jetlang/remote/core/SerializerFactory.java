package org.jetlang.remote.core;

import java.net.Socket;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:49 AM
 */
public interface SerializerFactory {

    Serializer create();

    default Serializer createForSocket(Socket socket) {
        return create();
    }

    default ObjectByteWriter createForGlobalWriter() {
        return create().getWriter();
    }

}

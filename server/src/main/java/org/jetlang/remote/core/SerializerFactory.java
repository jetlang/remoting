package org.jetlang.remote.core;

import java.net.Socket;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:49 AM
 */
public interface SerializerFactory {
    Serializer createForSocket(Socket socket);

    ObjectByteWriter createForGlobalWriter();

}

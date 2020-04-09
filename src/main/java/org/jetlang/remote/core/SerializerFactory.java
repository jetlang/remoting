package org.jetlang.remote.core;

import java.net.Socket;
import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:49 AM
 */
public interface SerializerFactory<R, W> {

    Serializer<R, W> create();

    default Serializer<R, W> createForSocket(Socket socket) {
        return create();
    }

    default ObjectByteWriter<W> createForGlobalWriter() {
        return create().getWriter();
    }

    default TopicReader createTopicReader(Charset charset){
        return new TopicReader.Cached(charset);
    }
}

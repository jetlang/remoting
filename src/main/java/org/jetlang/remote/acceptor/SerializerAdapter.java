package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.SerializerFactory;
import org.jetlang.remote.core.TcpSocket;
import org.jetlang.remote.core.TopicReader;

import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 11/29/11
 * Time: 9:25 AM
 */
public class SerializerAdapter<R, W> {

    private final Charset charset = Charset.forName("US-ASCII");
    private final SerializerFactory<R, W> ser;

    public SerializerAdapter(SerializerFactory<R, W> ser){
        this.ser = ser;
    }

    public BufferedSerializer<W> createBuffered(){
        return new BufferedSerializer<W>(charset, ser.createForGlobalWriter());
    }

    public Charset getCharset(){
        return charset;
    }

    public Serializer<R, W> createForSocket(TcpSocket socket) {
        return ser.createForSocket(socket.getSocket());
    }

    public TopicReader createTopicReader() {
        return ser.createTopicReader(charset);
    }
}

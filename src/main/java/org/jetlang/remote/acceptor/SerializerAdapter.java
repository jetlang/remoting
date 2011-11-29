package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.SerializerFactory;
import org.jetlang.remote.core.TcpSocket;

import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 11/29/11
 * Time: 9:25 AM
 */
public class SerializerAdapter {

    private final Charset charset = Charset.forName("US-ASCII");
    private final SerializerFactory ser;

    public SerializerAdapter(SerializerFactory ser){
        this.ser = ser;
    }

    public BufferedSerializer createBuffered(){
        return new BufferedSerializer(charset, ser.createForGlobalWriter());
    }

    public Charset getCharset(){
        return charset;
    }

    public Serializer createForSocket(TcpSocket socket) {
        return ser.createForSocket(socket.getSocket());
    }
}

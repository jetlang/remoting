package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.ClosableOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 2:26 PM
 */
public class CloseableByteArrayStream implements ClosableOutputStream {

    public final ByteArrayOutputStream data = new ByteArrayOutputStream();

    public OutputStream getOutputStream() {
        return data;
    }

    public boolean close() {
        try {
            data.close();
            return true;
        } catch (IOException ignored) {

        }
        return false;
    }

    public void reset() {
        data.reset();
    }
}

package com.jetlang.remote.acceptor;

import com.jetlang.remote.core.ClosableOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 2:26 PM
 */
public class CloseableByteArrayStream implements ClosableOutputStream {

    final ByteArrayOutputStream data = new ByteArrayOutputStream();

    public OutputStream getOutputStream() {
        return data;
    }

    public boolean close() {
        try {
            data.close();
            return true;
        } catch (IOException e) {

        }
        return false;
    }

    public void reset() {
        data.reset();
    }
}

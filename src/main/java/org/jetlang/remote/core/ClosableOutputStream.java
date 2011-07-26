package org.jetlang.remote.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 2:25 PM
 */
public interface ClosableOutputStream {

    OutputStream getOutputStream() throws IOException;

    boolean close();
}

package com.jetlang.remote.client;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * User: mrettig
 * Date: 4/5/11
 * Time: 11:39 AM
 */
public interface SocketConnector {
    Socket connect() throws IOException;
}

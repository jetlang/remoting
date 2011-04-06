package com.jetlang.remote.server;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 8:48 AM
 */
public interface MessageStreamWriter {
    void writeByteAsInt(int byteToWrite);

    void write(String topic, Object msg);
}

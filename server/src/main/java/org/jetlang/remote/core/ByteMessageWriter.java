package org.jetlang.remote.core;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 9:01 AM
 */
public interface ByteMessageWriter {
    void writeObjectAsBytes(byte[] buffer, int offset, int length);
}

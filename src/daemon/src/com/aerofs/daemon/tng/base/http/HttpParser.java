/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;

public class HttpParser
{
    private static final byte LF = '\n';

    private enum State
    {
        BEGIN,
        HEADERS,
        DONE,
    }

    public HttpMessage parse(InputStream stream)
            throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.allocate(8192);
        State state = State.BEGIN;
        HttpMessage message = null;

        while (state != State.DONE) {
            int data = stream.read();
            if (data == -1) {
                throw new EOFException("HttpMessage not complete");
            }

            buffer.put((byte) data);
            if ((byte) data != LF) {
                continue;
            }

            // We've read an entire line, so prepare the buffer for reading
            buffer.flip();

            // Read the line from the buffer
            String line = stringFromBuffer(buffer).trim();

            switch (state) {
            case BEGIN: {
                assert message == null;

                // Figure out if this HTTP message is a request or response
                String[] tokens = line.split(" ");
                if (tokens[0].startsWith("HTTP/")) {
                    message = parseResponseLine(tokens);
                } else {
                    message = parseRequestLine(tokens);
                }

                state = State.HEADERS;
                break;
            }

            case HEADERS: {
                assert message != null;

                if (line.isEmpty()) {
                    state = State.DONE;
                } else {
                    String[] tokens = line.split(":");
                    if (tokens.length != 2) {
                        throw new IOException("malformed header syntax: " + line);
                    }
                    message._headers.put(tokens[0].trim(), tokens[1].trim());
                }
                break;
            }
            default: assert false;
            }
            buffer.clear();
        }

        assert message != null;
        return message;
    }

    private static String stringFromBuffer(ByteBuffer buffer)
    {
        byte[] buf = new byte[buffer.remaining()];
        buffer.get(buf);
        return new String(buf, Charset.forName("UTF-8"));
    }

    private static HttpMessage parseResponseLine(String[] tokens)
            throws IOException
    {
        if (tokens.length < 2) {
            throw new IOException("invalid response line");
        }

        if (!tokens[0].equals("HTTP/1.1") && !tokens[0].equals("HTTP/1.0")) {
            throw new IOException("invalid HTTP version");
        }

        return HttpMessage.createResponse(Integer.valueOf(tokens[1]),
                Collections.<String, String>emptyMap());
    }

    private static HttpMessage parseRequestLine(String[] tokens)
            throws IOException
    {
        if (tokens.length != 3) {
            throw new IOException("invalid request line");
        }

        if (!tokens[2].equals("HTTP/1.1") && !tokens[2].equals("HTTP/1.0")) {
            throw new IOException("invalid HTTP version");
        }

        return HttpMessage.createRequest(HttpMessage.Method.valueOf(tokens[0]),
                URI.create(tokens[1]), Collections.<String, String>emptyMap());
    }

}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import com.aerofs.base.Base64;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PemDecoder
{
    private static byte[] BEGIN = "-----BEGIN ".getBytes();
    private static byte[] END = "-----END ".getBytes();

    public static InputStream decode(InputStream in) {
        return new Base64.InputStream(new PemInputStream(new BufferedInputStream(in)));
    }

    private static class PemInputStream extends InputStream
    {
        private final InputStream _in;
        private boolean _reading = false;
        private boolean _lineStart = true;

        PemInputStream(InputStream in)
        {
            if (in == null) throw new IllegalArgumentException("null input stream");
            _in = in;
        }

        @Override
        public int read()
                throws IOException
        {
//        byte[] b = new byte[1];
//        if (read(b) != 1) return -1;
//        return b[0] & 0xff;
            return read1();
        }

        @Override
        public int read(byte[] buffer, int offset, int count)
                throws IOException
        {
            return super.read(buffer, offset, count);
        }

        private int read1()
                throws IOException
        {
            while (true) {
                if (_lineStart) {
                    _lineStart = false;

                    // check for matching pattern
                    byte[] pattern = _reading ? END : BEGIN;
                    _in.mark(pattern.length);
                    boolean matched = true;
                    for (int i = 0; i < pattern.length; ++i) {
                        int c = _in.read();
                        if (c < 0 || c != pattern[i]) {
                            _in.reset();
                            matched = false;
                            break;
                        }
                    }

                    if (matched) {
                        _reading = !_reading;
                        // read to end of line
                        while (true) {
                            int c = _in.read();
                            if (c < 0) return c;
                            if (c == '\n' || c == '\r') {
                                break;
                            }
                        }
                        _lineStart = true;
                    }
                } else {
                    int c = _in.read();
                    if (c < 0) return c;
                    if (c == '\n' || c == '\r') {
                        _lineStart = true;
                    }
                    if (_reading) return c;
                }
            }
        }

        @Override
        public void close() throws IOException
        {
            _in.close();
        }
    }

}

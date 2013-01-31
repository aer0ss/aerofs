/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import com.aerofs.base.ex.ExFormatError;

import java.io.File;
import java.nio.charset.Charset;

public class BaseUtil
{

    private static final Charset CHARSET_UTF = Charset.forName("UTF-8");

    public static byte[] string2utf(String str)
    {
        return str.getBytes(CHARSET_UTF);
    }

    public static String utf2string(byte[] utf)
    {
        return new String(utf, CHARSET_UTF);
    }

    public static String hexEncode(byte[] bs, int offset, int length)
    {
        assert offset + length <= bs.length;

        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = offset; i < length; i++) {
            sb.append(String.format("%1$02x", bs[i]));
        }

        return sb.toString();
    }

    public static String hexEncode(byte[] bs)
    {
        return hexEncode(bs, 0, bs.length);
    }

    public static byte[] hexDecode(String str) throws ExFormatError
    {
        return hexDecode(str, 0, str.length());
    }

    public static byte[] hexDecode(String str, int start, int end) throws ExFormatError
    {
        int len = end - start;
        if (len % 2 != 0) throw new ExFormatError("wrong length");
        len >>= 1;

        byte[] bs = new byte[len];
        for (int i = 0; i < len; i++) {
            try {
                String pos = str.substring((i << 1) + start, (i << 1) + 2 + start);
                // Byte.parseByte() can't handle MSB==1
                bs[i] = (byte) Integer.parseInt(pos, 16);
            } catch (NumberFormatException e) {
                throw new ExFormatError(str + " contains invalid character");
            }
        }

        return bs;
    }

    public static byte[] concatenate(byte[] b1, byte[] b2)
    {
        byte[] ret = new byte[b1.length + b2.length];
        System.arraycopy(b1, 0, ret, 0, b1.length);
        System.arraycopy(b2, 0, ret, b1.length, b2.length);
        return ret;
    }

    /**
     * Recursively compute the total size in bytes of a directory.
     *
     * Note: this function will stack overflow if there are symlinks creating a circular directory
     * structure. You should only use this on directories where this should not happen.
     */
    public static long getDirSize(File f)
    {
        if (!f.exists() || (!f.isDirectory() && !f.isFile())) return 0;
        if (f.isFile()) return f.length();

        File[] children = f.listFiles();
        if (children == null || children.length == 0) return 0;

        long dirSize = 0;
        for (File child : children) {
            if (child.isFile()) {
                dirSize += child.length();
            } else {
                dirSize += getDirSize(child);
            }
        }

        return dirSize;
    }
}

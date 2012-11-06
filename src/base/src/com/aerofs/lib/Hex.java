/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.OutputStream;

public class Hex
{
    private Hex() {}

    /**
     * Get the value of a hex digit
     * @param ch the character
     * @return the numeric value, or -1 if not a digit
     */
    public static int digit(char ch)
    {
        return Character.digit(ch, 16);
    }

    /**
     * Get the hex character for a number
     * @param digit the number
     * @return the hex character
     */
    public static char forDigit(int digit)
    {
        return Character.forDigit(digit, 16);
    }

    public static byte decode(char c0, char c1) throws NumberFormatException
    {
        int h0 = digit(c0);
        int h1 = digit(c1);
        if (h0 < 0 || h1 < 0) throw new NumberFormatException("invalid character");
        byte b = (byte)((h0 << 4) + h1);
        return b;
    }

    private static int byteLength(int len) throws NumberFormatException
    {
        if (len < 0 || len % 2 != 0) throw new NumberFormatException("bad length");
        return len >> 1;
    }

    public static void decode(String str, int off, int len, OutputStream out)
        throws NumberFormatException, IOException
    {
        len = byteLength(len);
        for (int i = 0; i < len; ++i) {
            byte b = decode(str.charAt(off++), str.charAt(off++));
            out.write(b & 0xff);
        }
    }

    public static void decode(String str, int off, int len, byte[] bs, int bsoff)
            throws NumberFormatException
    {
        len = byteLength(len);
        for (int i = 0; i < len; ++i) {
            byte b = decode(str.charAt(off++), str.charAt(off++));
            bs[bsoff + i] = b;
        }
    }

    public static byte[] decode(String str, int off, int len) throws NumberFormatException
    {
        byte[] bs = new byte[len >> 1];
        decode(str, off, len, bs, 0);
        return bs;
    }

    public static byte[] decode(String str) throws NumberFormatException
    {
        return decode(str, 0, str.length());
    }


    public static void append(byte b, Appendable a)
            throws IOException
    {
        a.append(forDigit((b >> 4) & 0xf));
        a.append(forDigit((b >> 0) & 0xf));
    }

    public static void encode(byte[] bs, int off, int len, Appendable a)
            throws IOException
    {
        for (int i = 0; i < len; ++i) {
            byte b = bs[off + i];
            append(b, a);
        }
    }

    public static String encode(byte[] bs, int off, int len)
    {
        char[] buf = new char[len * 2];
        for (int i = 0; i < len; ++i) {
            byte b = bs[off + i];
            buf[2 * i + 0] = forDigit((b >> 4) & 0xf);
            buf[2 * i + 1] = forDigit((b >> 0) & 0xf);
        }
        return String.valueOf(buf);
    }

    public static String encode(byte[] bs)
    {
        return encode(bs, 0, bs.length);
    }

    public static String encode(ByteString bs, int off, int len)
    {
        char[] buf = new char[len * 2];
        for (int i = 0; i < len; ++i) {
            byte b = bs.byteAt(off + i);
            buf[2 * i + 0] = forDigit((b >> 4) & 0xf);
            buf[2 * i + 1] = forDigit((b >> 0) & 0xf);
        }
        return String.valueOf(buf);
    }

    public static String encode(ByteString bs)
    {
        return encode(bs, 0, bs.size());
    }
}

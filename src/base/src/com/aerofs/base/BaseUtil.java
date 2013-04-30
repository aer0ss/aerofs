/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import com.aerofs.base.ex.ExFormatError;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.Scanner;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.net.HttpURLConnection.HTTP_OK;

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
        char[] hex = new char[length * 2];
        hexEncode(bs, offset, length, hex, 0);
        return new String(hex);
    }

    private static final char[] hexDigits =
            {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

    public static void hexEncode(byte[] bs, int s_offset, int length, char[] hex, int d_offset)
    {
        for (int i = 0; i < length; ++i) {
            int v = bs[i + s_offset] & 0xFF;
            hex[i * 2 + d_offset + 0] = hexDigits[v >>> 4];
            hex[i * 2 + d_offset + 1] = hexDigits[v & 0x0F];
        }
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


    /**
     * Simple helper function to perform an HTTP request on a HttpURLConnection
     * Note: this function does not deal with character encodings properly. You should only
     * use it if you know that it won't be an issue.
     *
     * @param postData data to be POSTed to the HTTP server. If null, we will issue a GET instead.
     * @return the response from the server.
     * @throws IOException if the server doesn't respond with code 200, or if any other error occur
     */
    public static String httpRequest(HttpURLConnection connection, @Nullable String postData)
            throws IOException
    {
        final String CHARSET = "ISO-8859-1"; // This is the default HTTP charset

        OutputStream os = null;
        InputStream is = null;
        try {
            // Send
            if (postData != null && !postData.isEmpty()) {
                os = connection.getOutputStream();
                os.write(postData.getBytes(CHARSET));
            }

            // Receive
            int code = connection.getResponseCode();
            if (code != HTTP_OK) {
                throw new IOException("HTTP request failed. Code: " + code);
            }

            is = connection.getInputStream();
            return streamToString(is, CHARSET);
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        } finally {
            if (os != null) os.close();
            if (is != null) is.close();
        }
    }

    /**
     * Convert an input stream into a string using the specified charset.
     * Does not close the stream
     */
    private static String streamToString(InputStream is, String charset)
    {
        // See http://stackoverflow.com/a/5445161/365596
        // Basically, \A matches only the beginning of the input, thus making the Scanner return the
        // whole string
        java.util.Scanner scanner = new Scanner(is, checkNotNull(charset)).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}

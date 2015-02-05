/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.UniqueID;
import com.google.protobuf.ByteString;
import com.google.protobuf.LeanByteString;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

public class BaseUtil
{
    public static final Charset CHARSET_UTF = StandardCharsets.UTF_8;
    public static final char[] VALID_EMAIL_CHARS =
        new char[] { '.', '!', '#', '$', '%', '&', '\'', '*', '+', '-', '/',
                     '=', '?', '^', '_', '`', '{', '|', '}', '~' };

    public static byte[] string2utf(String str)
    {
        return str.getBytes(CHARSET_UTF);
    }

    public static String utf2string(byte[] utf)
    {
        return new String(utf, CHARSET_UTF);
    }

    public static byte[] toByteArray(long l)
    {
        return ByteBuffer.allocate(C.LONG_SIZE).putLong(l).array();
    }

    public static String truncateIfLongerThan(String s, int maxLength)
    {
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
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
            if (code != HTTP_OK && code != HTTP_CREATED && code != HTTP_NO_CONTENT) {
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
    public static String streamToString(InputStream is, String charset)
    {
        // See http://stackoverflow.com/a/5445161/365596
        // Basically, \A matches only the beginning of the input, thus making the Scanner return the
        // whole string
        java.util.Scanner scanner = new Scanner(is, checkNotNull(charset)).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    // the caller should throw ExInvalidCharacter if exception is needed
    public static boolean isValidEmailAddressToken(String part)
    {
        if (part.isEmpty()) return false;
        for (int i = 0; i < part.length(); i++) {
            char ch = part.charAt(i);
            if (ch >= 128) return false;    // must be ASCII
            if (Character.isLetterOrDigit(ch)) continue;
            boolean isValid = false;
            for (char valid : VALID_EMAIL_CHARS) {
                if (ch == valid) { isValid = true; break; }
            }
            if (!isValid) return false;
        }
        return true;
    }

    public static <T extends Comparable<T>> int compare(T[] a, T[] b)
    {
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            int comp = a[i].compareTo(b[i]);
            if (comp != 0) return comp;
        }

        return a.length - b.length;
    }

    /** Compare, where T is comparable to U (though not necessarily vice versa) */
    public static <U, T extends Comparable<? super U>> int compare(T a, U b)
    {
        if (a == null) {
            if (b == null) return 0;
            else return -1;
        } else {
            if (b == null) return 1;
            return a.compareTo(b);
        }
    }

    public static int compare(long a, long b)
    {
        if (a > b) return 1;
        else if (a == b) return 0;
        else return -1;
    }

    public static int compare(int a, int b)
    {
        if (a > b) return 1;
        else if (a == b) return 0;
        else return -1;
    }

    public static UniqueID fromPB(ByteString b)
    {
        return new UniqueID(new LeanByteString(b).getInternalByteArray());
    }

    public static ByteString toPB(UniqueID id)
    {
        return new LeanByteString(id.getBytes());
    }
}

/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc, 2011.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.Param;

import java.io.*;

/**
 * XMPP message body format (for both unicast and multicast)
 *
 * +--------------+-----+------+--------+
 * | MAGIC_NUMBER | len | data | chksum |
 * +--------------+-----+------+--------+
 */
public class XUtil
{
    /**
     *
     * @param bodylen
     * @return
     */
    public static byte[] writeHeader(int bodylen)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(getHeaderLen());
        DataOutputStream os = new DataOutputStream(bos);
        try {
            os.writeInt(Param.CORE_MAGIC);
            os.writeInt(bodylen);
            os.close();
        } catch (Exception e) {
            assert false;
        }

        return bos.toByteArray();
    }

    /**
     * @return body length
     */
    public static int readHeader(byte[] header) throws ExFormatError
    {
        try {
            assert header.length == getHeaderLen();
            DataInputStream is = new DataInputStream(new ByteArrayInputStream(header));
            int magic = is.readInt();
            if (magic != Param.CORE_MAGIC) {
                throw new ExFormatError("magic doesn't match. expect " +
                        Param.CORE_MAGIC + " received " + magic);
            }

            int len = is.readInt();
            if (len <= 0 || len > DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE) {
                throw new ExFormatError("insane msg len " + len);
            }

            return len;

        } catch (IOException e) {
            assert false;
            return -1;
        }
    }

    /**
     *
     * @return
     */
    public static int getHeaderLen()
    {
        return Integer.SIZE * 2 / Byte.SIZE;
    }

    /**
     * Helper method to print a digest of an encoded XMPP message
     *
     * @param body of which to print the digest
     * @return digest of the body
     */
    public static String getBodyDigest(String body)
    {
        if (body.length() <= 68) return body;
        else return body.substring(0, 64) + "...";
    }
}

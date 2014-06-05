/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Base64;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import static com.aerofs.daemon.lib.DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;

public abstract class XMPPUtilities
{
    static final int MAXCAST_UNFILTERED = -1;

    private static final Logger l = Loggers.getLogger(XMPPUtilities.class);

    private static final int HEADER_LEN = 2 * C.INTEGER_SIZE + 1;

    private XMPPUtilities()
    {
        // private to prevent instantiation
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

    //
    // message (de)serialization methods
    //

    public static String encodeBody(OutArg<Integer> outLen, byte[] ... bss)
    {
        return encodeBody(outLen, MAXCAST_UNFILTERED, bss);
    }

    public static String encodeBody(OutArg<Integer> serializedLength, int mcastid, byte[] ... bss)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            DataOutputStream os = new DataOutputStream(new Base64.OutputStream(bos));

            os.writeInt(LibParam.CORE_PROTOCOL_VERSION);

            // TODO consider adding mcastid to chksum?
            // if so, don't forget to check in decodeBody
            os.writeInt(mcastid);

            int len = 0;
            for (byte[] bs : bss) len += bs.length;
            os.writeInt(len);

            byte chksum = 0;
            for (byte[] bs : bss) {
                for (byte b : bs) chksum ^= b;
                os.write(bs);
            }

            os.write(chksum);

            os.close();

            // add the size of headers and footers
            serializedLength.set(len + HEADER_LEN);

        } catch (IOException e) {
            SystemUtil.fatal(e);
        }

        return bos.toString();
    }

    public static @Nullable byte[] decodeBody(DID did, OutArg<Integer> wireLength, String body, @Nullable MaxcastFilterReceiver maxcastFilterReceiver)
            throws IOException
    {
        ByteArrayInputStream bos = new ByteArrayInputStream(body.getBytes());
        DataInputStream is = null;
        try {
            is = new DataInputStream(new Base64.InputStream(bos));

            int magic = is.readInt();
            if (magic != LibParam.CORE_PROTOCOL_VERSION) {
                l.warn("{} magic mismatch exp:{} act:{} bdy:{}", did, LibParam.CORE_PROTOCOL_VERSION, magic, body);
                return null;
            }

            // Parse the maxcast id.
            int mcastid = is.readInt();

            if (maxcastFilterReceiver != null) {
                // Do not attempt to filter away if it is an UNFILTERED packet
                if (MAXCAST_UNFILTERED != mcastid && maxcastFilterReceiver.isRedundant(did, mcastid)) {
                    return null;
                }
            }

            int len = is.readInt();
            if ((len <= 0) || (len > MAX_TRANSPORT_MESSAGE_SIZE)) {
                throw new IOException("insane msg len " + len);
            }

            byte[] bs = new byte[len];
            try {
                is.readFully(bs);

                int read = is.read();
                if (read == -1) throw new IOException("chksum not present");

                byte chksum = (byte) read;
                for (byte b : bs) chksum ^= b;
                if (chksum != 0) throw new IOException("chksum mismatch");

            } catch (EOFException e) {
                throw new IOException("msg len " + len + " > actual");
            }

            if (bos.available() != 0) {
                throw new IOException("msg len " + len + " < avail by " + bos.available());
            }

            wireLength.set(len + HEADER_LEN);
            return bs;
        } finally {
            if (is != null) is.close();
        }
    }
}

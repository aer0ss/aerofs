/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.ssmp;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.lib.LibParam;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.*;
import java.util.Base64;

import static com.aerofs.daemon.lib.DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;

public abstract class SSMPUtil
{
    private static final Logger l = Loggers.getLogger(SSMPUtil.class);

    private SSMPUtil()
    {
        // private to prevent instantiation
    }

    public static String encodeMcastPayload(byte[] bs)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream os = new DataOutputStream(Base64.getEncoder().wrap(bos))) {
            byte chksum = 0;
            for (byte b : bs) chksum ^= b;

            os.writeInt(LibParam.CORE_PROTOCOL_VERSION);
            os.writeInt(bs.length);
            os.write(bs);
            os.write(chksum);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toString();
    }

    public static @Nullable byte[] decodeMcastPayload(DID did, String body)
            throws IOException
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(body.getBytes());
        try (DataInputStream is = new DataInputStream(Base64.getDecoder().wrap(bis))) {
            int magic = is.readInt();
            if (magic != LibParam.CORE_PROTOCOL_VERSION) {
                l.warn("{} magic mismatch exp:{} act:{} bdy:{}", did, LibParam.CORE_PROTOCOL_VERSION, magic, body);
                return null;
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

            if (bis.available() != 0) {
                throw new IOException("msg len " + len + " < avail by " + bis.available());
            }
            return bs;
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.injectable.InjectableFile;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import static com.google.common.base.Preconditions.checkState;

public class PrefixOutputStream extends DigestOutputStream
{
    private final static Logger l = Loggers.getLogger(PrefixOutputStream.class);

    private ContentHash h;
    private boolean closed;

    public PrefixOutputStream(OutputStream stream)
    {
        this(stream, BaseSecUtil.newMessageDigest());
    }

    public PrefixOutputStream(OutputStream stream, MessageDigest md)
    {
        super(stream, md);
    }

    public @Nonnull ContentHash digest()
    {
        checkState(closed);
        if (h == null) {
            h = new ContentHash(digest.digest());
        }
        return h;
    }

    @Override
    public void close() throws IOException
    {
        if (closed) return;
        try {
            super.flush();
            // we want to be extra sure that the file is synced to the disk and no write operation
            // is languishing in a kernel buffer for two reasons:
            //   * if the transaction commits we have to serve this file to other peers and it
            //     would be terribly uncool to serve a corrupted/partially synced copy
            //   * once the contents are written we adjust the file's mtime and if we allow a
            //     race between write() and utimes() we will end up with a timestamp mismatch
            //     between db and filesystem that cause spurious updates later on, thereby
            //     wasting CPU and bandwidth and causing extreme confusion for the end user.
            if (out instanceof FileOutputStream) {
                ((FileOutputStream)out).getChannel().force(true);
            }
        } finally {
            closed = true;
            super.close();
        }
    }

    public static InjectableFile hashFile(InjectableFile pf)
    {
        return pf.getParentFile().newChild(pf.getName() + ".hash");
    }

    public static void persistDigest(MessageDigest md, InjectableFile hf) throws IOException
    {
        try (FileOutputStream out = hf.newOutputStream()) {
            out.write(DigestSerializer.serialize(md));
            out.getChannel().force(true);
        }
    }

    public static MessageDigest partialDigest(InjectableFile pf, boolean append)
            throws IOException
    {
        InjectableFile hf = hashFile(pf);
        long prefixLength = pf.lengthOrZeroIfNotFile();
        if (!append || prefixLength == 0) {
            if (hf.exists()) {
                l.debug("discard partial hash {}", hf);
                hf.deleteIgnoreError();
            }
            return BaseSecUtil.newMessageDigest();
        }

        try {
            l.debug("load partial hash {} {}", hf, prefixLength);
            return DigestSerializer.deserialize(hf.toByteArray(), prefixLength);
        } catch (IllegalArgumentException|IOException e) {
            l.warn("failed to reload hash for {}", pf, e);
        }

        l.warn("discarding corrupted prefix {} {}",
                prefixLength, hf.lengthOrZeroIfNotFile());
        pf.deleteIgnoreError();
        hf.deleteIgnoreError();
        throw new IOException("corrupted prefix");
    }
}

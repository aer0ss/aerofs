/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.lib.ContentHash;
import com.aerofs.lib.SecUtil;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

class PrefixDownloadStream extends DigestOutputStream
{
    private ContentHash h;
    public PrefixDownloadStream(OutputStream stream)
    {
        this(stream, SecUtil.newMessageDigest());
    }

    public PrefixDownloadStream(OutputStream stream, MessageDigest md)
    {
        super(stream, md);
    }

    public @Nullable  ContentHash digest()
    {
        if (h == null) h = new ContentHash(digest.digest());
        return h;
    }

    @Override
    public void close() throws IOException
    {
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
        super.close();
    }
}

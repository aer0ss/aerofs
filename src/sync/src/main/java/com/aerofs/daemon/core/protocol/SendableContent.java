package com.aerofs.daemon.core.protocol;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nullable;

public class SendableContent {
    final SOKID sokid;
    final long mtime;
    final long length;
    final @Nullable ContentHash hash;
    final IPhysicalFile pf;

    public SendableContent(SOKID sokid, long mtime, long length, @Nullable ContentHash hash, IPhysicalFile pf) {
        this.sokid = sokid;
        this.mtime = mtime;
        this.length = length;
        this.hash = hash;
        this.pf = pf;
    }
}

package com.aerofs.daemon.core.protocol;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nullable;

public class SendableContent {
    public final SOKID sokid;
    public final long mtime;
    public final long length;
    public final @Nullable ContentHash hash;
    public final IPhysicalFile pf;

    public SendableContent(SOKID sokid, long mtime, long length, @Nullable ContentHash hash, IPhysicalFile pf) {
        this.sokid = sokid;
        this.mtime = mtime;
        this.length = length;
        this.hash = hash;
        this.pf = pf;
    }
}

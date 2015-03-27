package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.ids.DID;

import javax.annotation.Nonnull;

public final class StreamKey implements Comparable<StreamKey> {
    public final DID did;
    public final StreamID strmid;

    public StreamKey(DID sender, StreamID strmid) {
        this.did = sender;
        this.strmid = strmid;
    }

    @Override
    public boolean equals(Object o)
    {
        return o != null && (o == this || (o instanceof StreamKey
                && did.equals(((StreamKey) o).did)
                &&  strmid.equals(((StreamKey) o).strmid)));
    }

    @Override
    public int hashCode()
    {
        return did.hashCode() ^ strmid.getInt();
    }

    @Override
    public int compareTo(@Nonnull StreamKey key) {
        int ret = did.compareTo(key.did);
        return ret != 0 ? ret : strmid.compareTo(key.strmid);
    }

    @Override
    public String toString() {
        return did + ":" + strmid;
    }
}

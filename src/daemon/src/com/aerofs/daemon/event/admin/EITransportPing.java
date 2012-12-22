package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.base.id.DID;

import javax.annotation.Nullable;

/**
 * see IEOTransportPing. Additionally, the event can also throw not found
 * if the transport on which the peer is online has changed.
 * TODO may use the transport id all the way through to avoid the above situation
 */

public class EITransportPing extends AbstractEBIMC
{

    private final DID _did;
    private final int _seqPrev, _seqNext;
    private final boolean _forceNext;
    private final boolean _ignoreOffline;
    private @Nullable Long _rtt;

    public EITransportPing(DID did, int seqPrev, int seqNext,
            boolean forceNext, boolean ignoreOffline)
    {
        super(Core.imce());
        _did = did;
        _seqPrev = seqPrev;
        _seqNext = seqNext;
        _forceNext = forceNext;
        _ignoreOffline = ignoreOffline;
    }

    public DID did()
    {
        return _did;
    }

    public int seqPrev()
    {
        return _seqPrev;
    }

    public int seqNext()
    {
        return _seqNext;
    }

    public boolean forceNext()
    {
        return _forceNext;
    }

    public void setResult_(@Nullable Long rtt)
    {
        _rtt = rtt;
    }

    public @Nullable Long rtt()
    {
        return _rtt;
    }

    public boolean ignoreOffline()
    {
        return _ignoreOffline;
    }
}

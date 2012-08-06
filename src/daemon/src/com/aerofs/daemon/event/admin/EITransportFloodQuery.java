package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.id.DID;

/**
 * see IEOTransportFloodQuery. Additionally, the event can also throw not found
 * if the transport on which the peer is online has changed.
 * TODO pass the transport id along the way to avoid the above situation
 */
public class EITransportFloodQuery extends AbstractEBIMC
{

    private final DID _did;
    private final int _seq;

    private long _time;
    private long _bytes;

    public EITransportFloodQuery(DID did, int seq)
    {
        super(Core.imce());
        _did = did;
        _seq = seq;
    }

    public int seq()
    {
        return _seq;
    }

    public void setResult_(long time, long bytes)
    {
        _time = time;
        _bytes = bytes;
    }

    public long time_()
    {
        return _time;
    }

    public long bytes_()
    {
        return _bytes;
    }

    public DID did()
    {
        return _did;
    }
}

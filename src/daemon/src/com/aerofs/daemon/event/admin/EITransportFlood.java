package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.id.DID;

/**
 * see EOTransportFlood
 */
public class EITransportFlood extends AbstractEBIMC
{

    public final DID _did;
    public final boolean _send;
    public final int _seqStart, _seqEnd;
    public final long _duration;
    public final String _sname;

    public long _time;
    public long _bytes;

    public EITransportFlood(DID did, boolean send, int seqStart, int seqEnd,
            long duration, String sname)
    {
        super(Core.imce());
        _did = did;
        _send = send;
        _seqStart = seqStart;
        _seqEnd = seqEnd;
        _duration = duration;
        _sname = sname;
    }

    public void setResult_(long time, long bytes)
    {
        _time = time;
        _bytes = bytes;
    }
}

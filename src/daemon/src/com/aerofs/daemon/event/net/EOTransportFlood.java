package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.DID;

/**
 * used for gauging transport-to-transport bandwidth
 */
public class EOTransportFlood extends AbstractEBIMC
{

    public final DID _did;
    public final boolean _send;
    public final int _seqStart, _seqEnd;
    public final long _duration;

    public EOTransportFlood(DID did, boolean send, int seqStart, int seqEnd,
            long duration, IIMCExecutor imce)
    {
        super(imce);
        _did = did;
        _send = send;
        _seqStart = seqStart;
        _seqEnd = seqEnd;
        _duration = duration;
    }
}

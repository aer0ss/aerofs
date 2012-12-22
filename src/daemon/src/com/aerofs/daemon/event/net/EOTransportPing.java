package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.base.id.DID;

/**
 * send a new ping message with seq # seqNext() if 1) the ping message with
 * seq # seqPrev() is not remembered by the ping cache, 2) the pong message
 * corresponding to seqPrev() has been received, or 3) forceNext() is true.
 * In all other cases (case 4), the ping message is pending for receiving the
 * corresponding pong
 *
 * The resulting rtt is set to null in case 1, the actual RTT value
 * in case 2, and TRANSPORT_DIAGNOSIS_STATE_PENDING in case 3 and 4.
 *
 * The ping with seqPrev() is removed from the cache in case 2 and 3.
 *
 * The ping cache only remembers N recently requested ping messages. Therefore, N
 * only concurrently pings are supported. A method for application to tell if
 * there are too many concurrent pings is to test if a call with a value
 * seqPrev() returns null.
 *
 * The reason for such a convoluted design is that there's no techniques
 * (yet) in the core for cancelable inter-module calls. The entire thing should
 * be redesigned once cancelable IMCs are implemented.
 */
public class EOTransportPing extends AbstractEBIMC
{

    public final DID _did;
    public final int _seqPrev, _seqNext;
    public final boolean _forceNext;
    public Long _rtt;

    public EOTransportPing(DID did, int seqPrev, int seqNext,
            boolean forceNext, IIMCExecutor imce)
    {
        super(imce);
        _did = did;
        _seqPrev = seqPrev;
        _seqNext = seqNext;
        _forceNext = forceNext;
    }

    public void setResult_(Long rtt)
    {
        _rtt = rtt;
    }
}

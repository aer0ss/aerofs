package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

/**
 * used for gauging transport-to-transport bandwidth.
 *
 * execute() throws ExNotFound if the specified sequence number is unknown.
 * this may be caused by: 1) there are too many concurrent flood requests as
 * the transport only remembers a few most recently used sequence numbers, or
 * 2) there were errors during flooding
 */
public class EOTransportFloodQuery extends AbstractEBIMC
{

    /**
     * @param the sequence number to be queried. either seqStart or seqEnd
     * in IEOTransportFlood
     */
    public final int _seq;
    public long _time;
    public long _bytes;

    public EOTransportFloodQuery(int seq, IIMCExecutor imce)
    {
        super(imce);
        _seq = seq;
    }

    /**
     * @param time the time the peer receives the flood packet of the given
     *  sequence number. TRANSPORT_DIAGNOSIS_STATE_PENDING if the time has not
     *  been received
     *
     * @param bytes the number of bytes received by the peer at the time when
     * the peer receives the flood packet of the given sequence number. ignored
     * if time == TRANSPORT_DIAGNOSIS_STATE_PENDING
     */
    public void setResult_(long time, long bytes)
    {
        _time = time;
        _bytes = bytes;
    }
}

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.StreamManager.OutgoingStream;

// cannot use AbstractHdIMC here as we can't reply with okay until the message
// is actually sent
public class HdChunk implements IEventHandler<EOChunk>
{
    private final StreamManager _sm;
    private final IUnicast _ucast;

    public HdChunk(ITransportImpl tp)
    {
        _sm = tp.sm();
        _ucast = tp.ucast();
    }

    @Override
    public void handle_(EOChunk ev, Prio prio)
    {
        try {
            OutgoingStream ostrm = _sm.getOutgoingStreamThrows(ev._streamId, ev._seq);
            byte[][] bss = TPUtil.newPayload(ev._streamId, ev._seq, ev._sid, ev.byteArray());
            _ucast.send_(ostrm._did, ev, prio, bss, ostrm._cookie);
        } catch (Exception e) {
            ev.error(e);
        }
    }

}

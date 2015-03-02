package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.transport.lib.StreamManager.OutgoingStream;
import com.aerofs.lib.event.Prio;

// cannot use AbstractHdIMC here as we can't reply with okay until the message
// is actually sent
public class HdChunk implements IEventHandler<EOChunk>
{
    private final StreamManager streamManager;
    private final IUnicast unicast;

    public HdChunk(StreamManager streamManager, IUnicast unicast)
    {
        this.streamManager = streamManager;
        this.unicast = unicast;
    }

    @Override
    public void handle_(EOChunk ev, Prio prio)
    {
        try {
            OutgoingStream ostrm = streamManager.getOutgoingStreamThrows(ev._streamId);
            byte[][] bss = TransportProtocolUtil.newPayload(ev._streamId, ev._seq, ev.byteArray());
            unicast.send(ostrm._did, ev, prio, bss, ostrm._cookie);
        } catch (Exception e) {
            ev.error(e);
        }
    }
}

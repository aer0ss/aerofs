package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOBeginStream;
import com.aerofs.daemon.transport.lib.StreamManager.OutgoingStream;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBTPHeader;

import static com.aerofs.proto.Transport.PBStream.Type.BEGIN_STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;

public class HdBeginStream implements IEventHandler<EOBeginStream>
{
    private final StreamManager streamManager;
    private final IUnicast unicast;

    public HdBeginStream(StreamManager streamManager, IUnicast unicast)
    {
        this.streamManager = streamManager;
        this.unicast = unicast;
    }

    @Override
    public void handle_(EOBeginStream ev, Prio prio)
    {
        assert ev._seq == 0;

        try {
            DID did = ev._did;

            PBTPHeader h = PBTPHeader.newBuilder()
                    .setType(STREAM)
                    .setStream(PBStream
                            .newBuilder()
                            .setType(BEGIN_STREAM)
                            .setStreamId(ev._streamId.getInt()))
                    .build();

            // NB. we will not catch failures of sending ctrl msg. however it
            // will be reflected when sending the payload data below
            Object strmCookie = unicast.send(did, null, prio, TransportProtocolUtil.newControl(h), null);
            streamManager.newOutgoingStream(ev._streamId, new OutgoingStream(did, strmCookie));
            unicast.send(did, ev, prio, TransportProtocolUtil.newPayload(ev._streamId, 0, ev.byteArray()), strmCookie);
        } catch (Exception e) {
            ev.error(e);
        }
    }
}

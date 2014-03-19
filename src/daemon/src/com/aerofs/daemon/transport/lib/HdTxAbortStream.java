package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOTxAbortStream;
import com.aerofs.daemon.transport.lib.StreamManager.OutgoingStream;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import org.slf4j.Logger;

import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;

public class HdTxAbortStream implements IEventHandler<EOTxAbortStream>
{
    private static final Logger l = Loggers.getLogger(HdTxAbortStream.class);

    private final StreamManager streamManager;
    private final IUnicast unicast;

    public HdTxAbortStream(StreamManager streamManager, IUnicast unicast)
    {
        this.streamManager = streamManager;
        this.unicast = unicast;
    }

    @Override
    public void handle_(EOTxAbortStream ev, Prio prio)
    {
        try {
            OutgoingStream ostrm = streamManager.removeOutgoingStream(ev._streamId);
            if (ostrm == null) {
                l.warn("ostrm " + ev._streamId + " not found. ignore");
                return;
            }

            PBTPHeader h = PBTPHeader.newBuilder()
                    .setType(STREAM)
                    .setStream(PBStream
                            .newBuilder()
                            .setType(Type.TX_ABORT_STREAM)
                            .setStreamId(ev._streamId.getInt())
                            .setReason(ev._reason))
                    .build();

            unicast.send(ostrm._did, null, prio, TransportProtocolUtil.newControl(h), ostrm._cookie);
        } catch (Exception e) {
            l.warn("cannot abort stream " + ev._streamId + ". ignored: " + Util.e(e, ExDeviceOffline.class));
        }
    }
}

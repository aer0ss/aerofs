package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOTxAbortStream;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.StreamManager.OutgoingStream;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBTPHeader;

import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;

public class HdTxAbortStream implements IEventHandler<EOTxAbortStream>
{
    private final StreamManager _sm;
    private final IUnicast _ucast;

    public HdTxAbortStream(ITransportImpl tp)
    {
        _sm = tp.sm();
        _ucast = tp.ucast();
    }

    @Override
    public void handle_(EOTxAbortStream ev, Prio prio)
    {
        try {
            OutgoingStream ostrm = _sm.removeOutgoingStream(ev._streamId);
            if (ostrm == null) {
                Util.l(this).warn("ostrm " + ev._streamId + " not found. ignore");
                return;
            }

            PBTPHeader h = PBTPHeader.newBuilder()
                    .setType(STREAM)
                    .setStream(PBStream.newBuilder()
                            .setStreamId(ev._streamId.getInt())
                            .setReason(ev._reason))
                    .build();

            _ucast.send_(ostrm._did, null, prio, TPUtil.newControl(h), ostrm._cookie);
        } catch (Exception e) {
            Util.l(this).warn("cannot abort stream " + ev._streamId +
                    ". ignored: " + Util.e(e, ExDeviceOffline.class));
        }
    }
}

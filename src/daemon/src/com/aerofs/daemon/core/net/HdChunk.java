package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;

import static com.aerofs.proto.Transport.PBStream.InvalidationReason.STORE_NOT_FOUND;

/**
 * Handler for {@link EIChunk} events
 */
public class HdChunk implements IEventHandler<EIChunk>
{
    private static final Logger l = Loggers.getLogger(HdChunk.class);
    private final UnicastInputOutputStack _stack;
    private final Transports _tps;
    private final TC _tc;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdChunk(TC tc, Transports tps, UnicastInputOutputStack stack, IMapSID2SIndex sid2sidx)
    {
        _tc = tc;
        _tps = tps;
        _stack = stack;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void handle_(EIChunk ev, Prio prio)
    {
        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) {
            l.debug("no store " + ev._sid);
            // notify upper layers
            _stack.input().onStreamAborted_(ev._streamId, ev._ep, STORE_NOT_FOUND);
            // notify lower layers
            IIMCExecutor imce = _tps.getIMCE_(ev._ep.tp());
            EORxEndStream evEnd = new EORxEndStream(ev._ep.did(), ev._streamId, imce);
            try {
                CoreIMC.enqueueBlocking_(evEnd, _tc, Cat.UNLIMITED);
            } catch (Exception e) {
                SystemUtil.fatal(e);
            }
        } else {
            PeerContext pc = new PeerContext(ev._ep, sidx);
            RawMessage r = new RawMessage(ev.is(), ev.wireLength());
            _stack.input().onStreamChunkReceived_(ev._streamId, ev._seq, r, pc);
        }
    }
}

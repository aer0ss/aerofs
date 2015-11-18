package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.collector.Collector2;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.RPC.ExLinkDown;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.fetch.ApplyChange;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.proto.Core.*;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class FilterFetcher
{
    private static final Logger l = Loggers.getLogger(FilterFetcher.class);

    @Inject private RPC _rpc;
    @Inject private UpdateSenderFilter _pusf;
    @Inject private TransManager _tm;
    @Inject private MapSIndex2Store _sidx2s;
    @Inject private IMapSIndex2SID _sidx2sid;
    @Inject private IPulledDeviceDatabase _pulleddb;
    @Inject private TokenManager _tokenManager;
    @Inject private ChangeEpochDatabase _cedb;
    @Inject private CoreExponentialRetry _cer;
    @Inject private CoreScheduler _sched;
    @Inject private Devices _devices;
    @Inject private ApplyChange _ac;

    private static class Req {
        private final DID did;
        private final SIndex sidx;

        Req(SIndex sidx, DID did) {
            this.did = did;
            this.sidx = sidx;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Req && ((Req) o).did.equals(did) && ((Req) o).sidx.equals(sidx);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sidx, did);
        }
    }

    private final Map<Req, Boolean> _scheduled = new HashMap<>();

    public void scheduleFetch_(DID did, SIndex sidx) {
        Req r = new Req(sidx, did);
        Boolean sched = _scheduled.get(r);
        if (sched != null && sched) return;
        _scheduled.put(r, true);
        if (sched == null) {
            schedRequest_(r);
        }
    }

    private void schedRequest_(Req r) {
        _sched.schedule_(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                retryRequest_(r);
            }
        });
    }

    private void retryRequest_(Req r) {
        _cer.retry("gf:" + r.sidx + ":" + r.did, () -> {
            request_(r);
            return null;
        }, ExNotFound.class);
    }

    private void request_(Req r) throws Exception
    {
        Token tk = _tokenManager.acquire_(Cat.HOUSEKEEPING, "gf:" + r.sidx + ":" + r.did);
        if (tk == null) {
            _tokenManager.addTokenReclamationListener_(Cat.HOUSEKEEPING, cascade -> {
                retryRequest_(r);
                cascade.run();
            });
            return;
        }
        try {
            _scheduled.put(r, false);
            issueRequest_(r.did, r.sidx, tk);
            Boolean scheduled = _scheduled.get(r);
            if (scheduled != null && scheduled) {
                schedRequest_(r);
            } else {
                _scheduled.remove(r);
            }
        } catch (ExDeviceUnavailable|ExLinkDown e) {
            l.debug("{} no longer online", r.did);
            _scheduled.remove(r);
        } catch (Exception e) {
            _scheduled.put(r, true);
            throw e;
        } finally {
            tk.reclaim_();
        }
    }

    private void issueRequest_(DID did, SIndex sidx, Token tk)
            throws Exception
    {
        SID sid = _sidx2sid.getNullable_(sidx);
        if (sid == null) return;

        if (!_devices.getOnlinePotentialMemberDevices_(sidx).containsKey(did)) return;

        PBGetFilterRequest.Builder bd = PBGetFilterRequest.newBuilder();
        bd.setStoreId(BaseUtil.toPB(sid));
        bd.setFromBase(!_pulleddb.contains_(sidx, did));
        l.debug("{} gf request for {}: {}", did, sidx, bd.getFromBase());

        PBCore request = CoreProtocolUtil.newRequest(Type.GET_FILTER_REQUEST)
                .setGetFilterRequest(bd).build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.writeDelimitedTo(out);

        DigestedMessage msg;
        try {
            msg = _rpc.issueRequest_(did, request, tk, "gf");
        } catch (ExAborted e) {
            if (e.getCause() != null) {
                Throwables.propagateIfInstanceOf(e.getCause(), ExDeviceUnavailable.class);
                throw Throwables.propagate(e.getCause());
            }
            throw e;
        }
        processResponse_(sidx, msg);
    }

    private void processResponse_(SIndex sidx, DigestedMessage msg)
            throws Exception
    {
        if (msg.pb().hasExceptionResponse()) throw Exceptions.fromPB(msg.pb().getExceptionResponse());
        Util.checkPB(msg.pb().hasGetFilterResponse(), PBGetFilterResponse.class);

        // store may have disappeared while we were waiting for a response
        if (_sidx2sid.getNullable_(sidx) == null) return;

        DID from = msg.did();
        PBGetFilterResponse pb = msg.pb().getGetFilterResponse();

        if (!pb.hasSenderFilter()) return;

        if (!(_pulleddb.contains_(sidx, from) || pb.getFromBase())) {
            // RACE RACE RACE
            // if we send a GetFilter request and then discard collector filters before
            // receiving the response we MUST discard any filter in the response.
            // The only safe way to discard filters is to discard the entire response
            throw new ExRetryLater("race");
        }

        Long c = _cedb.getChangeEpoch_(sidx);
        if (c == null || c < pb.getSenderFilterEpoch() || _ac.hasBufferedChanges_(sidx)) {
            // RACE RACE RACE
            // if we get a fresh bloom filter before we fetch the corresponding remote content
            // from polaris we run the risk of discarding the filter before we ever have the
            // chance to add the corresponding objects to the collector queue
            //
            // NB: we also wait for all buffered changes to be applied, otherwise migrated
            // objects in the buffer might be added to the collector queue after the change
            // epoch is updated and the collector filters may be discarded too early
            // Ideally we would only wait for the buffered changes before the filter epoch
            // but that would require storing the exact transform epoch in the meta buffer
            // entry. In practice it's highly unlikely that clients would run into a long
            // enough sequence of buffered updates that fine-grained filter application
            // becomes important.
            // TODO: schedule change fetcher?
            throw new ExRetryLater("missing changes");
        }

        BFOID filter = new BFOID(pb.getSenderFilter());

        l.info("{} receive gf response for {} {}", from, sidx, filter);

        try (Trans t = _tm.begin_()) {
            _sidx2s.getThrows_(sidx).iface(Collector2.class).add_(from, filter, t);

            // Once all blocks have been processed and are written to the db,
            // locally remember that store s has been pulled from the DID from.
            _pulleddb.insert_(sidx, from, t);
            t.commit_();
        }
        _pusf.send_(sidx, pb.getSenderFilterIndex(), pb.getSenderFilterUpdateSeq(), from);
    }
}

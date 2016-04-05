package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.collector.ContentFetcher;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.RPC.ExLinkDown;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.polaris.async.AsyncTask;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler.TaskState;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.fetch.ApplyChange;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.transport.lib.OutgoingStream;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.proto.Core.*;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Map.Entry;


public class FilterFetcher
{
    private static final Logger l = Loggers.getLogger(FilterFetcher.class);

    @Inject private RPC _rpc;
    @Inject private UpdateSenderFilter _pusf;
    @Inject private TransManager _tm;
    @Inject private MapSIndex2Store _sidx2s;
    @Inject private IMapSIndex2SID _sidx2sid;
    @Inject private IMapSID2SIndex _sid2sidx;
    @Inject private IPulledDeviceDatabase _pulleddb;
    @Inject private TokenManager _tokenManager;
    @Inject private ChangeEpochDatabase _cedb;
    @Inject private CoreExponentialRetry _cer;
    @Inject private Devices _devices;
    @Inject private ApplyChange _ac;
    @Inject private Metrics _m;
    @Inject private TransportRoutingLayer _trl;

    private class DeviceState implements AsyncTask {
        final DID did;
        final TaskState s;
        Set<SIndex> req = new HashSet<>();

        DeviceState(DID d) {
            did = d;
            s = _sched.register_("gf:"+ d, this);
        }

        @Override
        public void run_(AsyncTaskCallback cb) {
            Set<SIndex> sidxs = req;
            req = new HashSet<>();
            if (!request_(did, sidxs, cb)) {
                l.debug("done {}", did);
                cb.onSuccess_(false);
            }
        }
    }

    private AsyncWorkGroupScheduler _sched;
    private Map<DID, DeviceState> _states = new HashMap<>();

    @Inject
    public FilterFetcher(CoreScheduler sched) {
        _sched = new AsyncWorkGroupScheduler(sched);
    }

    private DeviceState state(DID did) {
        DeviceState ds = _states.get(did);
        if (ds == null) {
            ds = new DeviceState(did);
            _states.put(did, ds);
            ds.s.start_();
        }
        return ds;
    }

    public void scheduleFetch_(DID did, SIndex sidx) {
        DeviceState ds = state(did);
        ds.req.add(sidx);
        ds.s.schedule_();
    }

    private boolean request_(DID did, Set<SIndex> sidxs, AsyncTaskCallback cb) {
        Device d = _devices.getOPMDevice_(did);
        if (d == null || sidxs.isEmpty()) {
            l.debug("{} stop: offline", did);
            return false;
        }
        Endpoint ep = new Endpoint(d.getPreferredTransport_(), did);
        try {
            issueRequest_(ep, sidxs, cb);
        } catch (ExLinkDown e) {
            l.debug("{} stop: link down", did);
            return false;
        } catch (Throwable t) {
            cb.onFailure_(t);
        }
        return true;
    }

    private void issueRequest_(Endpoint ep, Set<SIndex> sidxs, AsyncTaskCallback cb)
            throws Exception
    {
        Map<SID, SIndex> sids = new HashMap<>();
        for (SIndex sidx : sidxs) {
            SID sid = _sidx2sid.getNullable_(sidx);
            if (sid == null) {
                l.debug("{} absent {}", ep.did(), sidx);
                continue;
            }
            sids.put(sid, sidx);
        }

        if (sids.isEmpty()) {
            cb.onSuccess_(false);
            return;
        }

        l.debug("gf request {} {}", ep, sidxs);

        PBCore request = CoreProtocolUtil.newRequest(Type.GET_FILTER_REQUEST)
                .setGetFilterRequest(PBGetFilterRequest.newBuilder()
                        .setCount(sids.size()))
                .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.writeDelimitedTo(out);

        OutgoingStream os = null;
        int maxUcastLen = _m.getMaxUnicastSize_();

        for (Entry<SID, SIndex> e : sids.entrySet()) {
            boolean pulled = _pulleddb.contains_(e.getValue(), ep.did());
            PBGetFilterRequest.Store rs = PBGetFilterRequest.Store.newBuilder()
                    .setStoreId(BaseUtil.toPB(e.getKey()))
                    .setFromBase(!pulled)
                    .build();
            if (!pulled) l.debug("{} first req for {}", ep.did(), e.getValue());
            if (out.size() + C.INTEGER_SIZE + rs.getSerializedSize() > maxUcastLen) {
                if (os == null) {
                    os = ep.tp().newOutgoingStream(ep.did());
                }
                os.write(out.toByteArray());
                out = new ByteArrayOutputStream();
            }
            rs.writeDelimitedTo(out);
        }
        if (os != null) {
            os.write(out.toByteArray());
            os.close();
        } else {
            _trl.sendUnicast_(ep, CoreProtocolUtil.typeString(request.getType()),
                    request.getRpcid(), out);
        }

        _rpc.asyncRequest_(ep, request, new FutureCallback<DigestedMessage>() {
            @Override
            public void onSuccess(DigestedMessage result) {
                try {
                    processResponse_(sidxs, result);
                    cb.onSuccess_(true);
                } catch (Throwable t) {
                    onFailure(t);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                cb.onFailure_(t);
            }
        });
    }

    private void processResponse_(Set<SIndex> sidxs, DigestedMessage msg)
            throws Exception
    {
        if (msg.pb().hasExceptionResponse()) throw Exceptions.fromPB(msg.pb().getExceptionResponse());
        Util.checkPB(msg.pb().hasGetFilterResponse(), PBGetFilterResponse.class);

        DID from = msg.did();

        Set<SIndex> failed = new HashSet<>();

        for (int i = 0; i < msg.pb().getGetFilterResponse().getCount(); ++i) {
            PBGetFilterResponse.Store pb = PBGetFilterResponse.Store.parseDelimitedFrom(msg.is());
            SID sid = new SID(BaseUtil.fromPB(pb.getStoreId()));
            SIndex sidx = _sid2sidx.getNullable_(sid);

            // store may have disappeared while we were waiting for a response
            if (sidx == null) {
                continue;
            }

            if (!sidxs.contains(sidx)) throw new ExProtocolError();

            if (pb.hasEx()) {
                failed.add(sidx);
                continue;
            }

            if (!pb.hasSenderFilter()) continue;

            if (!(_pulleddb.contains_(sidx, from) || pb.getFromBase())) {
                // RACE RACE RACE
                // if we send a GetFilter request and then discard collector filters before
                // receiving the response we MUST discard any filter in the response.
                // The only safe way to discard filters is to discard the entire response
                l.warn("{} race {}", from, sidx);
                failed.add(sidx);
                continue;
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
                l.warn(" {} missing changes {}: {} {}", from, sidx, c, pb.getSenderFilterEpoch());
                failed.add(sidx);
                continue;
            }

            BFOID filter = new BFOID(pb.getSenderFilter());

            l.info("{} receive gf response for {} {}", from, sidx, filter);

            try (Trans t = _tm.begin_()) {
                _sidx2s.get_(sidx).iface(ContentFetcher.class).add_(from, filter, t);
                _pulleddb.insert_(sidx, from, t);
                t.commit_();
            }
            // TODO: single message for ACKs
            _pusf.send_(sidx, pb.getSenderFilterIndex(), pb.getSenderFilterUpdateSeq(), from);
        }

        if (!failed.isEmpty()) {
            DeviceState ds = state(from);
            ds.req.addAll(failed);

            if (failed.size() == sidxs.size()) {
                throw new ExRetryLater("incomplete filter fetch: " + failed);
            }
        }
    }
}

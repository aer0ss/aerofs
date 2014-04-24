/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.quota;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import static com.aerofs.daemon.lib.DaemonParam.CHECK_QUOTA_INTERVAL;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Sp.CheckQuotaCall.PBStoreUsage;
import com.aerofs.proto.Sp.CheckQuotaReply.PBStoreShouldCollect;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class QuotaEnforcement implements IQuotaEnforcement
{
    private static final Logger l = Loggers.getLogger(QuotaEnforcement.class);

    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;
    private final MapSIndex2Store _sidx2s;
    private final CoreScheduler _sched;
    private final IStores _stores;
    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final SPBlockingClient.Factory _factSP;
    private final StoreUsageCache _usage;

    @Inject
    public QuotaEnforcement(IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid, MapSIndex2Store sidx2s,
            CoreScheduler sched, IStores stores, TokenManager tokenManager, TransManager tm,
            InjectableSPBlockingClientFactory factSP, StoreUsageCache usage)
    {
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _sidx2s = sidx2s;
        _sched = sched;
        _stores = stores;
        _tokenManager = tokenManager;
        _tm = tm;
        _factSP = factSP;
        _usage = usage;
    }

    @Override
    public void start_()
    {
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    enforceQuotas_();
                } catch (Exception e) {
                    // Don't retry on errors. Wait for the next opportunity.
                    l.warn("error in check quota. ignored. {}", Util.e(e));
                }

                _sched.schedule(this, CHECK_QUOTA_INTERVAL);
            }
        }, 0);
    }

    /**
     * Collect data usage for all the stores, call SP.checkQuota(), and turn on and off content
     * collection according to the reply from SP.
     */
    private void enforceQuotas_()
            throws Exception
    {
        Map<SID, Long> sid2usage = getBytesUsed_();
        Map<SID, Boolean> sid2result = checkQuotas_(sid2usage);
        toggleContentCollection_(sid2result);
    }

    /**
     * Return a map from Stores to their data usage.
     */
    private Map<SID, Long> getBytesUsed_()
            throws SQLException
    {
        Map<SID, Long> sid2usage = Maps.newHashMap();
        for (SIndex sidx : _stores.getAll_()) {
            sid2usage.put(_sidx2sid.get_(sidx), _usage.getBytesUsed_(sidx));
        }
        return sid2usage;
    }

    /**
     * Call SP.checkQuota() and return the result, which dictate whether each store should turn on
     * or off content collection.
     */
    private Map<SID, Boolean> checkQuotas_(Map<SID, Long> sid2usage)
            throws Exception
    {
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "quota");
        try {
            TCB tcb = tk.pseudoPause_("quota");
            try {
                return fromPB(_factSP.create()
                        .signInRemote()
                        .checkQuota(toPB(sid2usage)).getStoreList());
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    /**
     * Encode the parameter of SP.checkQuota() to protobuf format
     */
    private List<PBStoreUsage> toPB(Map<SID, Long> sid2usage)
    {
        List<PBStoreUsage> pbUsage = Lists.newArrayListWithCapacity(sid2usage.size());
        for (Entry<SID, Long> en : sid2usage.entrySet()) {
            pbUsage.add(PBStoreUsage.newBuilder()
                    .setSid(en.getKey().toPB())
                    .setBytesUsed(en.getValue())
                    .build());
        }
        return pbUsage;
    }

    /**
     * Decode the reply of SP.checkQuota() from protobuf format
     */
    private Map<SID, Boolean> fromPB(List<PBStoreShouldCollect> pbReply)
    {
        Map<SID, Boolean> sid2result = Maps.newHashMap();
        for (PBStoreShouldCollect en : pbReply) {
            sid2result.put(new SID(en.getSid()), en.getCollectContent());
        }
        return sid2result;
    }

    /**
     * Turn on or off content collection on all the stores. A 'true' value in the map turns on
     * content collection on the corresponding store.
     */
    private void toggleContentCollection_(Map<SID, Boolean> sid2bool)
            throws SQLException
    {
        Trans t = _tm.begin_();
        try {
            for (Entry<SID, Boolean> en : sid2bool.entrySet()) {
                SID sid = new SID(en.getKey());
                if (en.getValue()) includeContent_(sid, t);
                else excludeContent_(sid);
            }
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Nop if the store is not present locally (due to deletion during the SP call) or the content
     * is already being collected.
     */
    private void includeContent_(SID sid, Trans t)
            throws SQLException
    {
        Store s = getStoreNullable_(sid);
        if (s == null) return;

        if (s.collector().includeContent_()) {
            // Well, since we've skipped all the content components in the collector queue, reset
            // filters so we can start collecting them.
            s.resetCollectorFiltersForAllDevices_(t);
        }
    }

    /**
     * Nop if the store is not present locally (due to deletion during the SP call) or the content
     * is already excluded from collection.
     */
    private void excludeContent_(SID sid)
    {
        Store s = getStoreNullable_(sid);
        if (s == null) return;

        s.collector().excludeContent_();
    }

    private @Nullable Store getStoreNullable_(SID sid)
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) {
            l.info("store {} not present. ignore", sid);
            return null;
        } else {
            return _sidx2s.get_(sidx);
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.quota;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.*;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.tc.Cat;
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

import static com.aerofs.daemon.lib.DaemonParam.CHECK_QUOTA_INTERVAL;

public class QuotaEnforcement implements IQuotaEnforcement
{
    private static final Logger l = Loggers.getLogger(QuotaEnforcement.class);

    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;
    private final MapSIndex2Store _sidx2s;
    private final CoreScheduler _sched;
    private final StoreHierarchy _stores;
    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final SPBlockingClient.Factory _factSP;
    private final DirectoryService _ds;

    @Inject
    public QuotaEnforcement(IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid, MapSIndex2Store sidx2s,
            CoreScheduler sched, StoreHierarchy stores, TokenManager tokenManager, TransManager tm,
            InjectableSPBlockingClientFactory factSP, DirectoryService ds)
    {
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _sidx2s = sidx2s;
        _sched = sched;
        _stores = stores;
        _tokenManager = tokenManager;
        _tm = tm;
        _factSP = factSP;
        _ds = ds;
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
    public void enforceQuotas_()
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
            sid2usage.put(_sidx2sid.get_(sidx), _ds.getBytesUsed_(sidx));
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
        return _tokenManager.inPseudoPause_(Cat.UNLIMITED, "quota", () -> {
                    return fromPB(_factSP.create()
                            .signInRemote()
                            .checkQuota(toPB(sid2usage))
                            .getStoreList());
                }
        );
    }

    /**
     * Encode the parameter of SP.checkQuota() to protobuf format
     */
    private List<PBStoreUsage> toPB(Map<SID, Long> sid2usage)
    {
        List<PBStoreUsage> pbUsage = Lists.newArrayListWithCapacity(sid2usage.size());
        for (Entry<SID, Long> en : sid2usage.entrySet()) {
            pbUsage.add(PBStoreUsage.newBuilder()
                    .setSid(BaseUtil.toPB(en.getKey()))
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
            sid2result.put(new SID(BaseUtil.fromPB(en.getSid())), en.getCollectContent());
        }
        return sid2result;
    }

    /**
     * Turn on or off content collection for all the stores mentioned in the parameter. A 'true'
     * value in the parameter turns on content collection on the corresponding store.
     */
    private void toggleContentCollection_(Map<SID, Boolean> sid2bool)
            throws SQLException
    {
        try (Trans t = _tm.begin_()) {
            for (Entry<SID, Boolean> en : sid2bool.entrySet()) {
                SID sid = new SID(en.getKey());
                Store s = getStoreNullable_(sid);
                // TODO(phoenix)
                if (s == null || !(s instanceof LegacyStore)) continue;

                if (en.getValue()) {
                    ((LegacyStore)s).startCollectingContent_(t);
                } else {
                    ((LegacyStore)s).stopCollectingContent_(t);
                }
            }
            t.commit_();
        }
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

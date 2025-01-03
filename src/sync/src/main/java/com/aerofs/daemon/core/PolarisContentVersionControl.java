/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.core.protocol.NewUpdatesSender;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.ids.OID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

public class PolarisContentVersionControl implements IContentVersionControl
{
    private final CentralVersionDatabase _cvdb;
    private final ContentChangesDatabase _ccdb;
    private final ContentFetchQueueDatabase _cfqdb;
    private final MapSIndex2Store _sidx2s;
    private final NewUpdatesSender _nus;
    private final CoreExponentialRetry _cer;
    private final CoreScheduler _sched;
    private final ChangeEpochDatabase _cedb;
    private final List<IContentVersionListener> _listeners;

    @Inject
    public PolarisContentVersionControl(CentralVersionDatabase cvdb, ChangeEpochDatabase cedb,
            ContentChangesDatabase ccdb, ContentFetchQueueDatabase cfqdb, MapSIndex2Store sidx2s,
            NewUpdatesSender nus, CoreExponentialRetry cer, CoreScheduler sched,
            Set<IContentVersionListener> listeners)
    {
        _cvdb = cvdb;
        _ccdb = ccdb;
        _cfqdb = cfqdb;
        _sidx2s = sidx2s;
        _nus = nus;
        _cer = cer;
        _sched = sched;
        _cedb = cedb;
        if (listeners != null) {
            _listeners = Lists.newArrayList(listeners);
        } else {
            _listeners = Collections.emptyList();
        }
    }

    private void notifyListeners_(SIndex sidx, OID oid, long v, Trans t) throws SQLException {
        for (IContentVersionListener listener : _listeners) {
            listener.onSetVersion_(sidx, oid, v, t);
        }
    }

    private static class Updated {
        private final Set<OID> oids = new HashSet<>();
        private long maxEpoch = 0;

        public void add(OID oid, long v) {
            oids.add(oid);
            maxEpoch = Math.max(maxEpoch, v);
        }
    }

    private final TransLocal<Map<SIndex, Updated>> _tlUpdated = new TransLocal<Map<SIndex, Updated>>() {
        @Override
        protected Map<SIndex, Updated> initialValue(Trans t) {
            Map<SIndex, Updated> m = new HashMap<>();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committing_(Trans t) throws SQLException {
                    for (Entry<SIndex, Updated> e : m.entrySet()) {
                        Store s = _sidx2s.getNullable_(e.getKey());
                        if (s == null) continue;
                        SenderFilters sf = s.iface(SenderFilters.class);
                        for (OID oid : e.getValue().oids) {
                            sf.objectUpdated_(oid, t);
                        }
                        long ts = Objects.firstNonNull(_cedb.getContentChangeEpoch_(e.getKey()), 0L);
                        if (ts < e.getValue().maxEpoch) {
                            _cedb.setContentChangeEpoch_(e.getKey(), e.getValue().maxEpoch, t);
                        }
                    }
                }

                @Override
                public void committed_() {
                    // TODO: better scheduling
                    _sched.schedule(new AbstractEBSelfHandling() {
                        @Override
                        public void handle_() {
                            for (Entry<SIndex, Updated> e : m.entrySet()) {
                                _cer.retry("nus", () -> {
                                    _nus.sendForStore_(e.getKey(), e.getValue().maxEpoch);
                                    return null;
                                });
                            }
                        }
                    });
                }
            });
            return m;
        }
    };

    public void setContentVersion_(SIndex sidx, OID oid, long v, long lts, Trans t)
            throws SQLException {
        Updated u = _tlUpdated.get(t).get(sidx);
        if (u == null) {
            u = new Updated();
            _tlUpdated.get(t).put(sidx, u);
        }
        u.add(oid, lts);
        // see comment in ApplyChange#applyContentChange_
        Long pv = _cvdb.getVersion_(sidx, oid);
        if (pv == null || pv != v) {
            _cvdb.setVersion_(sidx, oid, v, t);
        }
        // NB: notify listener even when the version doesn't change, otherwise
        // available content may not be advertised after sharing/migration
        notifyListeners_(sidx, oid, v, t);
    }

    @Override
    public void fileExpelled_(SOID soid, Granularity g, Trans t) throws SQLException
    {
        // NB: we do not clear the central version because migration relies on its continued
        // presence for deleted objects to avoid false content conflicts.
        // Instead we clean it up on re-admission
        if (g != Granularity.Store) {
            _ccdb.deleteChange_(soid.sidx(), soid.oid(), t);
            _cfqdb.remove_(soid.sidx(), soid.oid(), t);
        }
        // for accurate reporting of available versions we need high-granularity processing
        // even when expelling an entire store
        for (IContentVersionListener listener : _listeners) {
            listener.onContentUnavailable_(soid.sidx(), soid.oid(), t);
        }
    }
}

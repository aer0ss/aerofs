/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.SSMPNotificationSubscriber;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.status.ISyncStatusPropagator;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.ssmp.SSMPResponse;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * subscribes to sync notifications for stores of interest
 */
public class SyncNotificationSubscriber extends SSMPNotificationSubscriber
        implements ISyncNotificationSubscriber
{
    private final static Logger l = Loggers.getLogger(SyncNotificationSubscriber.class);

    private final IMapSID2SIndex _sid2sidx;
    private final ISyncStatusPropagator _sync;
    private final CentralVersionDatabase _cvdb;
    private final CoreScheduler _sched;
    private final TransManager _tm;

    @Inject
    public SyncNotificationSubscriber(SSMPConnection ssmp, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx, ISyncStatusPropagator sync, CentralVersionDatabase cvdb,
            CoreScheduler sched, TransManager tm) {
        super(ssmp, sidx2sid);
        _sid2sidx = sid2sidx;
        _sync = sync;
        _cvdb = cvdb;
        _sched = sched;
        _tm = tm;
    }

    @Override
    protected String getStoreTopic(SID sid) {
        return "sync/" + sid.toStringFormal();
    }

    @Override
    protected FutureCallback<SSMPResponse> subscribeCallback(SID sid) {
        return new FutureCallback<SSMPResponse>() {
            @Override
            public void onSuccess(SSMPResponse r) {
                if (r.code == SSMPResponse.OK) {
                    l.warn("subscribed to polaris notif for {}", sid);
                } else {
                    l.error("failed to polaris sub {} {}", sid, r.code);
                    // TODO: exp retry?
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                // TODO: force reconnect?
            }
        };
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        if (ev.type != Type.MCAST || !ev.to.toString().startsWith("sync/") || !ev.from.isAnonymous()) {
            return;
        }
        try {
            handleResponse(ev.to, ev.payload);
        } catch (Exception e) {
            l.warn("invalid notification", e);
        }
    }

    private void handleResponse(SSMPIdentifier to, byte[] payload) throws ExInvalidID {
        SID store = new SID(to.toString().substring(5));
        _sched.schedule_(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                SyncedLocationDeserializer iterator = new SyncedLocationDeserializer(payload);
                SIndex sidx = _sid2sidx.getNullable_(store);
                if (sidx == null) return;
                try (Trans t = _tm.begin_()) {
                    while (iterator.hasNext()) {
                        SyncedLocation location = iterator.next();

                        SOID soid = new SOID(sidx, location.oid);

                        boolean isLatestVersion;
                        try {
                            isLatestVersion = ((Long) location.version)
                                    .equals(_cvdb.getVersion_(soid.sidx(), soid.oid()));
                        } catch (SQLException e) {
                            l.error("Error looking up current version of file", e);
                            isLatestVersion = false;
                        }
                        if (isLatestVersion) {
                            _sync.updateSyncStatus_(soid, isLatestVersion, t);
                        }
                    }
                    t.commit_();
                } catch (SQLException e) {
                    l.error("error updating sync status", e);
                }
            }
        });
    }

    private final class SyncedLocation
    {
        static final int SIZE = UniqueID.LENGTH + Long.BYTES;
        private OID oid;
        private long version;

        // TODO: handle varint encoded version
        public SyncedLocation(OID oid, long version) {
            this.oid = oid;
            this.version = version;
        }
    }

    private final class SyncedLocationDeserializer implements Iterator<SyncedLocation>
    {
        private final byte[] payload;
        private int index;

        public SyncedLocationDeserializer(byte[] payload) {
            assert payload.length % SyncedLocation.SIZE == 0 : "invalid byte[] input for SyncedLocation";
            this.payload = payload;
            this.index = 0;
        }

        @Override
        public boolean hasNext() {
            return index < payload.length;
        }

        @Override
        public SyncedLocation next() {
            OID oid = new OID(Arrays.copyOfRange(payload, index, index + UniqueID.LENGTH));
            index += UniqueID.LENGTH;
            long version = Longs.fromByteArray(Arrays.copyOfRange(payload, index, index + Long.BYTES));
            index += Long.BYTES;

            return new SyncedLocation(oid, version);
        }
    }
}

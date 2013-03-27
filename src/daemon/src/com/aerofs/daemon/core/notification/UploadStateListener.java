package com.aerofs.daemon.core.notification;

import java.sql.SQLException;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.IUploadStateListener;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.lib.Path;
import com.aerofs.lib.Throttler;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import javax.annotation.Nullable;

class UploadStateListener implements IUploadStateListener
{
    private final RitualNotificationServer _notifier;
    private final DirectoryService _ds;
    private final TC _tc;
    private final UserAndDeviceNames _nr; // name resolver

    private final Throttler<Key, Value> _throttler = new Throttler.Builder<Key, Value>()
            .setDelay(1 * C.SEC)
            .setThrottleFilter(new Predicate<Value>()
            {
                @Override
                public boolean apply(@Nullable Value value)
                {
                    return value != null && value._done < value._total;
                }
            })
            .setUntrackFilter(Predicates.<Value>alwaysTrue())
            .build();

    UploadStateListener(RitualNotificationServer notifier, DirectoryService ds, TC tc,
            UserAndDeviceNames nr)
    {
        _notifier = notifier;
        _ds = ds;
        _tc = tc;
        _nr = nr;
    }

    @Override
    public void stateChanged_(Key key, Value value)
    {
        if (_throttler.shouldThrottle(key, value)) return;

        _notifier.sendEvent_(state2pb_(_tc, _ds, _nr, key, value));
    }

    static PBNotification state2pb_(TC tc, DirectoryService ds, UserAndDeviceNames nr,
            Key key, Value value)
    {
        assert tc.isCoreThread();

        SOCID socid = key._socid;
        PBSOCID pbsocid = PBSOCID.newBuilder()
                .setSidx(socid.sidx().getInt())
                .setOid(socid.oid().toPB())
                .setCid(socid.cid().getInt())
                .build();

        DID did = key._ep.did();

        PBUploadEvent.Builder bd = PBUploadEvent.newBuilder()
                .setSocid(pbsocid)
                .setDeviceId(did.toPB())
                .setDisplayName(nr.getDisplayName_(did))
                .setDone(value._done)
                .setTotal(value._total);

        Path path;
        try {
            path = ds.resolveNullable_(socid.soid());
        } catch (SQLException e) {
            Loggers.getLogger(UploadStateListener.class).warn(Util.e(e));
            path = null;
        }

        if (path != null) bd.setPath(path.toPB());

        return PBNotification.newBuilder()
                .setType(Type.UPLOAD)
                .setUpload(bd)
                .build();
    }
}

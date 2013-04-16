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
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;

class UploadStateListener implements IUploadStateListener
{
    private final RitualNotificationServer _notifier;
    private final DirectoryService _ds;
    private final UserAndDeviceNames _nr; // name resolver

    private final Throttler<Key> _throttler = new Throttler<Key>(1 * C.SEC);
    private final boolean _useTransferFilter = Cfg.useTransferFilter();

    UploadStateListener(RitualNotificationServer notifier, DirectoryService ds,
            UserAndDeviceNames nr)
    {
        _notifier = notifier;
        _ds = ds;
        _nr = nr;
    }

    @Override
    public void stateChanged_(Key key, Value value)
    {
        if (_useTransferFilter && key._socid.cid().isMeta()) return;

        if (value._done < value._total) {
            if (_throttler.shouldThrottle(key)) return;
        } else {
            _throttler.untrack(key);
        }

        _notifier.sendEvent_(state2pb_(_ds, _nr, key, value));
    }

    static PBNotification state2pb_(DirectoryService ds, UserAndDeviceNames nr,
            Key key, Value value)
    {
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

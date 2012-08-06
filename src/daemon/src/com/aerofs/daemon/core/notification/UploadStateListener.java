package com.aerofs.daemon.core.notification;

import java.sql.SQLException;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.IUploadStateListener;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBSOCKID;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;

class UploadStateListener implements IUploadStateListener
{
    private final RitualNotificationServer _notifier;
    private final DirectoryService _ds;
    private final TC _tc;

    UploadStateListener(RitualNotificationServer notifier, DirectoryService ds, TC tc)
    {
        _notifier = notifier;
        _ds = ds;
        _tc = tc;
    }

    @Override
    public void stateChanged_(Key key, Value value)
    {
        _notifier.sendEvent_(state2pb_(_tc, _ds, key, value));
    }

    static PBNotification state2pb_(TC tc, DirectoryService ds, Key key, Value value)
    {
        assert tc.isCoreThread();

        SOCKID k = key._k;
        PBSOCKID pbk = PBSOCKID.newBuilder()
                .setSidx(k.sidx().getInt())
                .setOid(k.oid().toPB())
                .setCid(k.cid().getInt())
                .setKidx(k.kidx().getInt())
                .build();

        PBUploadEvent.Builder bd = PBUploadEvent.newBuilder()
                .setK(pbk)
                .setDeviceId(key._ep.did().toPB())
                .setDone(value._done)
                .setTotal(value._total);

        Path path;
        try {
            path = ds.resolveNullable_(k.soid());
        } catch (SQLException e) {
            Util.l(UploadStateListener.class).warn(Util.e(e));
            path = null;
        }

        if (path != null) bd.setPath(path.toPB());

        return PBNotification.newBuilder()
                .setType(Type.UPLOAD)
                .setUpload(bd)
                .build();
    }
}

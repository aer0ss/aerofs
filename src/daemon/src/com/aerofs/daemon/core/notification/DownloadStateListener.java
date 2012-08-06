package com.aerofs.daemon.core.notification;

import java.sql.SQLException;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.IDownloadStateListener;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBSOCKID;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;

class DownloadStateListener implements IDownloadStateListener
{
    private final RitualNotificationServer _notifier;
    private final DirectoryService _ds;
    private final TC _tc;

    DownloadStateListener(RitualNotificationServer notifier, DirectoryService ds, TC tc)
    {
        _notifier = notifier;
        _ds = ds;
        _tc = tc;
    }

    @Override
    public void stateChanged_(SOCKID k, State state)
    {
        _notifier.sendEvent_(state2pb_(_tc, _ds, k, state));
    }

    static PBNotification state2pb_(TC tc, DirectoryService ds, SOCKID k, State state)
    {
        assert tc.isCoreThread();

        PBSOCKID pbk = PBSOCKID.newBuilder()
            .setSidx(k.sidx().getInt())
            .setOid(k.oid().toPB())
            .setCid(k.cid().getInt())
            .setKidx(k.kidx().getInt())
            .build();

        PBDownloadEvent.Builder bd = PBDownloadEvent.newBuilder().setK(pbk);

        Path path;
        try {
            path = ds.resolveNullable_(k.soid());
        } catch (SQLException e) {
            Util.l(DownloadStateListener.class).warn(Util.e(e));
            path = null;
        }

        if (path != null) bd.setPath(path.toPB());

        if (state instanceof Started) {
            bd.setState(PBDownloadEvent.State.STARTED);
        } else if (state instanceof Ended) {
            bd.setState(PBDownloadEvent.State.ENDED);
            bd.setOkay(((Ended) state)._okay);
        } else if (state instanceof Enqueued) {
            bd.setState(PBDownloadEvent.State.ENQUEUED);
        } else {
            assert state instanceof Ongoing;
            bd.setState(PBDownloadEvent.State.ONGOING);
            bd.setDone(((Ongoing) state)._done);
            bd.setTotal(((Ongoing) state)._total);
            bd.setDeviceId(((Ongoing) state)._ep.did().toPB());
        }

        return PBNotification.newBuilder()
                .setType(Type.DOWNLOAD)
                .setDownload(bd)
                .build();
    }
}

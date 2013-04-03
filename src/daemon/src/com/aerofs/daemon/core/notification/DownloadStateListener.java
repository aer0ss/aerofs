package com.aerofs.daemon.core.notification;

import java.sql.SQLException;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.protocol.IDownloadStateListener;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.lib.Path;
import com.aerofs.lib.Throttler;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;

class DownloadStateListener implements IDownloadStateListener
{
    private final RitualNotificationServer _notifier;
    private final DirectoryService _ds;
    private final TC _tc;
    private final UserAndDeviceNames _nr; // name resolver

    private final Throttler<SOCID> _throttler = new Throttler<SOCID>(1 * C.SEC);
    private final boolean _useTransferFilter = Cfg.useTransferFilter();

    DownloadStateListener(RitualNotificationServer notifier, DirectoryService ds, TC tc,
            UserAndDeviceNames nr)
    {
        _notifier = notifier;
        _ds = ds;
        _tc = tc;
        _nr = nr;
    }

    @Override
    public void stateChanged_(SOCID socid, State state)
    {
        if (_useTransferFilter && socid.cid().isMeta()) return;

        if (state instanceof Ongoing) {
            if (_throttler.shouldThrottle(socid)) return;
        } else if (state instanceof Ended) {
            _throttler.untrack(socid);
        } else if (_useTransferFilter) return;

        _notifier.sendEvent_(state2pb_(_tc, _ds, _nr, socid, state));
    }

    static PBNotification state2pb_(TC tc, DirectoryService ds, UserAndDeviceNames nr,
            SOCID socid, State state)
    {
        assert tc.isCoreThread();

        PBSOCID pbsocid = PBSOCID.newBuilder()
            .setSidx(socid.sidx().getInt())
            .setOid(socid.oid().toPB())
            .setCid(socid.cid().getInt())
            .build();

        PBDownloadEvent.Builder bd = PBDownloadEvent.newBuilder().setSocid(pbsocid);

        Path path;
        try {
            path = ds.resolveNullable_(socid.soid());
        } catch (SQLException e) {
            Loggers.getLogger(DownloadStateListener.class).warn(Util.e(e));
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

            DID did = ((Ongoing) state)._ep.did();

            bd.setDeviceId(did.toPB());
            bd.setDisplayName(nr.getDisplayName_(did));
            bd.setDone(((Ongoing) state)._done);
            bd.setTotal(((Ongoing) state)._total);
        }

        return PBNotification.newBuilder()
                .setType(Type.DOWNLOAD)
                .setDownload(bd)
                .build();
    }
}

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.CoreProtocolReactor;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.inject.Inject;
import org.slf4j.Logger;

/**
 * This class is responsible for reacting to NEW_UPDATE messages
 */
public class NewUpdates implements CoreProtocolReactor.Handler
{
    private static final Logger l = Loggers.getLogger(NewUpdates.class);

    private final IMapSID2SIndex _sid2sidx;
    private final LocalACL _lacl;
    private final Impl _impl;

    public static class Impl {
        @Inject private ChangeEpochDatabase _cedb;
        @Inject private MapSIndex2Store _sidx2s;
        @Inject private FilterFetcher _ff;

        public void handle_(SIndex sidx, DID did, PBNewUpdates pb) throws Exception {
            if (!pb.hasChangeEpoch()) throw new ExProtocolError("recv pre-phoenix notif");

            Long epoch = _cedb.getChangeEpoch_(sidx);
            if (epoch == null || pb.getChangeEpoch() > epoch) {
                l.debug("{}: {} > {} -> fetch from polaris", sidx, pb.getChangeEpoch(), epoch);
                _sidx2s.get_(sidx).iface(ChangeFetchScheduler.class).schedule_();
            }

            _ff.scheduleFetch_(did, sidx);
        }
    }

    @Inject
    public NewUpdates(IMapSID2SIndex sid2sidx, LocalACL lacl, Impl impl)
    {
        _sid2sidx = sid2sidx;
        _lacl = lacl;
        _impl = impl;
    }

    @Override
    public Type message() {
        return Type.NEW_UPDATES;
    }

    @Override
    public void handle_(DigestedMessage msg) throws Exception
    {
        l.debug("{} process incoming nu over {}", msg.did(), msg.tp());

        if (!msg.pb().hasNewUpdates()) throw new ExProtocolError();
        PBNewUpdates pb = msg.pb().getNewUpdates();
        SIndex sidx = _sid2sidx.getThrows_(new SID(BaseUtil.fromPB(pb.getStoreId())));

        // see Rule 2 in acl.md. Note that the maxcast sender can forge the device id
        // (unless maxcast messages are signed). therefore this is not a security measure.
        // see more in acl.md.
        if (!_lacl.check_(msg.user(), sidx, Permissions.EDITOR)) {
            l.warn("{} ({}) on {} has no editor perm for {}", msg.did(), msg.user(), sidx);
            return;
        }

        _impl.handle_(sidx, msg.did(), pb);
    }
}

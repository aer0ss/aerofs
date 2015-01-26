package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

/**
 * This class is responsible for reacting to NEW_UPDATE messages
 */
public class NewUpdates
{
    private static final Logger l = Loggers.getLogger(NewUpdates.class);

    private final IMapSID2SIndex _sid2sidx;
    private final LocalACL _lacl;
    private final AntiEntropy _ae;
    private final ChangeEpochDatabase _cedb;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public NewUpdates(IMapSID2SIndex sid2sidx, LocalACL lacl, AntiEntropy ae,
            ChangeEpochDatabase cedb, MapSIndex2Store sidx2s)
    {
        _sid2sidx = sid2sidx;
        _lacl = lacl;
        _ae = ae;
        _cedb = cedb;
        _sidx2s = sidx2s;
    }

    public void process_(DigestedMessage msg)
            throws ExNotFound, SQLException, IOException, ExProtocolError
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

        Long epoch = _cedb.getChangeEpoch_(sidx);
        if (epoch == null) {
            if (pb.hasChangeEpoch()) throw new ExProtocolError("recv phoenix notif");

            // TODO: epidemic propagation
            // TODO: impact AE scheduling
            _ae.request_(sidx, msg.did());
        } else {
            if (!pb.hasChangeEpoch()) throw new ExProtocolError("recv pre-phoenix notif");

            if (pb.getChangeEpoch() > epoch) {
                l.debug("{}: {} > {} -> fetch from polaris", sidx, pb.getChangeEpoch(), epoch);
                _sidx2s.get_(sidx).fetchChanges_();
            }
        }
    }
}

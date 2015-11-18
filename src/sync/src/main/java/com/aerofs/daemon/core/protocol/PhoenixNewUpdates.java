package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetchScheduler;
import com.aerofs.daemon.core.protocol.NewUpdates.Impl;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.ids.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class PhoenixNewUpdates implements Impl {
    private final static Logger l = Loggers.getLogger(PhoenixNewUpdates.class);

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

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.ids.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.inject.Inject;

public class LegacyNewUpdates extends NewUpdates.Impl {
    private final AntiEntropy _ae;

    @Inject
    public LegacyNewUpdates(AntiEntropy ae) {
        _ae = ae;
    }

    @Override
    public void handle_(SIndex sidx, DID did, PBNewUpdates pb) throws Exception {
        if (pb.hasChangeEpoch()) throw new ExProtocolError("recv phoenix notif");

        // TODO: epidemic propagation
        _ae.request_(sidx, did);
    }
}

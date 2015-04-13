package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.protocol.ContentUpdater.ReceivedContent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;

public class TransitionalCausality implements Causality {
    private final LegacyCausality _legacy;
    private final PolarisCausality _polaris;

    @Inject
    public TransitionalCausality(LegacyCausality legacy, PolarisCausality polaris)
    {
        _legacy = legacy;
        _polaris = polaris;
    }


    @Override
    public @Nullable CausalityResult computeCausality_(SOID soid, ReceivedContent content)
            throws Exception {
        if (content.vRemote.unwrapCentral() != null) {
            return _polaris.computeCausality_(soid, content);
        } else {
            return _legacy.computeCausality_(soid, content);
        }
    }

    @Override
    public void updateVersion_(SOKID k, ReceivedContent content, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted {
        if (content.vRemote.unwrapCentral() != null) {
            _polaris.updateVersion_(k, content, res, t);
        } else {
            _legacy.updateVersion_(k, content, res, t);
        }
    }
}

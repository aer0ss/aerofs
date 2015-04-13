package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.protocol.ContentUpdater.ReceivedContent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

public interface Causality {
    @Nullable CausalityResult computeCausality_(SOID soid, ReceivedContent content)
            throws Exception;

    /**
     * Delete obsolete branches, update version vectors
     */
    void updateVersion_(SOKID k, ReceivedContent content, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted;
}

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Grab bag of content-related methods that differ between daemon and SA
 */
public interface ContentProvider {
    KIndex pickBranch(SOID soid) throws SQLException, ExNotFound, ExUpdateInProgress;

    boolean hasUnacknowledgedLocalChange(SOID soid) throws SQLException;

    SendableContent content(SOKID k) throws SQLException, ExNotFound, IOException;

    IPhysicalFile fileWithMatchingContent(SOID soid, ContentHash h) throws SQLException, ExNotFound;

    void apply_(IPhysicalPrefix prefix, IPhysicalFile pf, long replyMTime, ContentHash h, Trans t)
            throws Exception;
}

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase.ContentChange;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public class ContentSubmitConflictHandler {
    protected final static Logger l = Loggers.getLogger(ContentSubmitConflictHandler.class);

    // return false if the conflict is resolved
    boolean onConflict_(ContentChange c, LocalChange lc, String body)
            throws SQLException {
        l.info("conflict {}{}: {}", c.sidx, c.oid, lc.localVersion, lc.hash);
        // TODO: ?
        return true;
    }
}

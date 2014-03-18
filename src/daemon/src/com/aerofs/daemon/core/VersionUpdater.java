package com.aerofs.daemon.core;

import java.io.IOException;
import java.sql.SQLException;

import javax.annotation.Nonnull;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.lib.id.SOCKID;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class increments version vectors due to local component updates
 *
 * TODO don't include KIndex in parameters, writing non-master branches is not allowed after all.
 */
public class VersionUpdater
{
    private static final Logger l = Loggers.getLogger(VersionUpdater.class);

    private final NativeVersionControl _nvc;

    @Inject
    public VersionUpdater(NativeVersionControl nvc)
    {
        _nvc = nvc;
    }

    /**
     * Use this method to increment non-alias versions.
     */
    public void update_(SOCKID k, @Nonnull Trans t)
        throws SQLException, IOException
    {
        updateImpl_(k, false, t);
    }

    /**
     * Use this method to increment alias versions.
     */
    public void updateAliased_(SOCKID k, @Nonnull Trans t)
        throws SQLException, IOException
    {
        updateImpl_(k, true, t);
    }

    private void updateImpl_(SOCKID k, boolean alias, @Nonnull Trans t)
        throws SQLException, IOException
    {
        checkNotNull(t);
        l.debug("update {}", k);

        _nvc.updateMyVersion_(k, alias, t);
    }
}

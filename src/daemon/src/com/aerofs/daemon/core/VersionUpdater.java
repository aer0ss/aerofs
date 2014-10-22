package com.aerofs.daemon.core;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Nonnull;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.lib.id.SOCKID;

import static com.google.common.base.Preconditions.checkArgument;
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
    private final ChangeEpochDatabase _cedb;
    private final MetaChangesDatabase _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final DirectoryService _ds;

    @FunctionalInterface
    public interface IListener
    {
        public void updated_(SOID soid, Trans t);
    }

    private final List<IListener> _listeners = Lists.newArrayList();

    @Inject
    public VersionUpdater(NativeVersionControl nvc, ChangeEpochDatabase cedb,
            MetaChangesDatabase mcdb, ContentChangesDatabase ccdb, DirectoryService ds)
    {
        _nvc = nvc;
        _cedb = cedb;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _ds = ds;
    }

    public void addListener_(IListener l)
    {
        _listeners.add(l);
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

        if (_cedb.getChangeEpoch_(k.sidx()) != null) {
            checkArgument(!alias);
            checkArgument(k.kidx().isMaster());
            if (k.cid().isMeta()) {
                OA oa = _ds.getOA_(k.soid());
                _mcdb.insertChange_(k.sidx(), k.oid(), oa.parent(), oa.name(), t);
            } else {
                checkArgument(k.cid().isContent());
                _ccdb.insertChange_(k.sidx(), k.oid(), t);
            }
            for (IListener l : _listeners) l.updated_(k.soid(), t);
        } else {
            _nvc.updateMyVersion_(k, alias, t);
        }
    }
}

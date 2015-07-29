package com.aerofs.daemon.core;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SOCKID;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class increments version vectors due to local component updates
 *
 * TODO don't include KIndex in parameters, writing non-master branches is not allowed after all.
 */
public class VersionUpdater implements IVersionUpdater
{
    private static final Logger l = Loggers.getLogger(VersionUpdater.class);

    private final NativeVersionControl _nvc;
    private final CfgUsePolaris _usePolaris;
    private final MetaChangesDatabase _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final DirectoryService _ds;

    private final List<IListener> _listeners = Lists.newArrayList();

    @Inject
    public VersionUpdater(NativeVersionControl nvc, CfgUsePolaris usePolaris,
            MetaChangesDatabase mcdb, ContentChangesDatabase ccdb, DirectoryService ds)
    {
        _nvc = nvc;
        _usePolaris = usePolaris;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _ds = ds;
    }

    @Override
    public void addListener_(IListener l)
    {
        _listeners.add(l);
    }

    /**
     * Use this method to increment non-alias versions.
     */
    @Override
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

        if (_usePolaris.get()) {
            checkArgument(!alias);
            checkArgument(k.kidx().isMaster());
            if (k.cid().isMeta()) {
                OA oa = _ds.getOA_(k.soid());
                // FIXME(phoenix): special handling for deletion:
                // - remove all changes if the object is not known remotely
                // - [care must be taken to account for in-flight changes]
                // - ?
                _mcdb.insertChange_(k.sidx(), k.oid(), oa.parent(), oa.name(), t);
            } else {
                checkArgument(k.cid().isContent());
                _ccdb.insertChange_(k.sidx(), k.oid(), t);
            }
            for (IListener l : _listeners) l.updated_(k, t);
        } else {
            _nvc.updateMyVersion_(k, alias, t);
        }
    }
}

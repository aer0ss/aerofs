package com.aerofs.daemon.core;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOCID;
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
 * This update the local change tables for eventual submission to polaris
 */
public class VersionUpdater implements IVersionUpdater
{
    private static final Logger l = Loggers.getLogger(VersionUpdater.class);

    private final MetaChangesDatabase _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final DirectoryService _ds;

    private final List<IListener> _listeners = Lists.newArrayList();

    @Inject
    public VersionUpdater(MetaChangesDatabase mcdb, ContentChangesDatabase ccdb, DirectoryService ds)
    {
        _mcdb = mcdb;
        _ccdb = ccdb;
        _ds = ds;
    }

    @Override
    public void addListener_(IListener l)
    {
        _listeners.add(l);
    }

    @Override
    public void update_(SOCID socid, @Nonnull Trans t)
        throws SQLException, IOException
    {
        checkNotNull(t);
        l.debug("update {}", socid);

        if (socid.cid().isMeta()) {
            OA oa = _ds.getOA_(socid.soid());
            // FIXME(phoenix): special handling for deletion:
            // - remove all changes if the object is not known remotely
            // - [care must be taken to account for in-flight changes]
            // - ?
            _mcdb.insertChange_(socid.sidx(), socid.oid(), oa.parent(), oa.name(), t);
        } else {
            checkArgument(socid.cid().isContent());
            _ccdb.insertChange_(socid.sidx(), socid.oid(), t);
        }
        for (IListener l : _listeners) l.updated_(socid, t);
    }
}

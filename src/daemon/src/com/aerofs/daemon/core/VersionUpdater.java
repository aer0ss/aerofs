package com.aerofs.daemon.core;

import java.io.IOException;
import java.sql.SQLException;

import javax.annotation.Nonnull;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class increments version vectors due to local component updates. It also maintains
 * consistency between the logical and physical objects and pushes NEW_UPDATE messages for these
 * updates. That is why this class is separate from NativeVersionControl.
 *
 * TODO don't include KIndex in parameters, writing non-master branches is not allowed after all.
 */
public class VersionUpdater
{
    private static final Logger l = Loggers.getLogger(VersionUpdater.class);

    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;

    @Inject
    public VersionUpdater(NativeVersionControl nvc, DirectoryService ds, IPhysicalStorage ps)
    {
        _nvc = nvc;
        _ds = ds;
        _ps = ps;
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

        if (!k.cid().isMeta()) {
            // Update length and mtime on the logical file to be consistent with the physical file.
            // The linker relies on these fields to detect file changes.
            IPhysicalFile pf = _ps.newFile_(_ds.resolve_(k.soid()), k.kidx());

            long length = pf.getLength_();
            long mtime = pf.getLastModificationOrCurrentTime_();

            l.info("update ca {} {} {}", k, length, mtime);

            // We are about to set a null hash, which is allowed only on master branches. See Hasher
            // for detail.
            assert k.kidx().equals(KIndex.MASTER) : k;
            _ds.setCA_(k.sokid(), length, mtime, null, t);
        }
    }
}

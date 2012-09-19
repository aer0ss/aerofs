package com.aerofs.daemon.core;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import javax.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.proto.NewUpdates;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.lib.db.trans.Trans;
import org.apache.log4j.Logger;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.Util;
import com.google.common.collect.Sets;

/**
 * This class increments version vectors due to local component updates. It also maintains
 * consistency between the logical and physical objects and pushes NEW_UPDATE messages for these
 * updates. That is why this class is separate from NativeVersionControl.
 *
 * TODO don't include KIndex in parameters, writing non-master branches is not allowed after all.
 */
public class VersionUpdater
{
    private static final Logger l = Util.l(VersionUpdater.class);

    // This set remembers all the branches that have been updated since the last NEW_UPDATE message
    // was sent.
    private final Set<SOCKID> _updated = Sets.newTreeSet();

    private DelayedScheduler _dsNewUpdateMessage;
    private NativeVersionControl _nvc;
    private NewUpdates _nu;
    private DirectoryService _ds;

    @Inject
    public void inject_(NewUpdates nu, NativeVersionControl nvc, DirectoryService ds,
            CoreScheduler sched)
    {
        _nu = nu;
        _nvc = nvc;
        _ds = ds;

        _dsNewUpdateMessage = new DelayedScheduler(sched, DaemonParam.NEW_UPDATE_MESSAGE_DELAY,
                new Runnable() {
                    @Override
                    public void run()
                    {
                        assert !_updated.isEmpty();

                        try {
                            // send a NEW_UPDATE message for all the branches that have been updated
                            // since the last NEW_UPDATE.
                            _nu.send_(_updated);
                        } catch (Exception e) {
                            // failed to push.
                            l.warn("ignored: " + Util.e(e));
                        }

                        _updated.clear();
                    }
                });
    }

    /**
     * Use this method to increment non-alias versions.
     */
    public void update_(SOCKID k, Trans t)
        throws SQLException, IOException
    {
        updateImpl_(k, false, t);
    }

    /**
     * Use this method to increment alias versions.
     */
    public void updateAliased_(SOCKID k, Trans t)
        throws SQLException, IOException
    {
        updateImpl_(k, true, t);
    }

    private void updateImpl_(SOCKID k, boolean alias, Trans t)
        throws SQLException, IOException
    {
        if (l.isInfoEnabled()) l.info("update " + k);
        assert t != null;

        _nvc.updateMyVersion_(k, alias, t);

        if (!k.cid().isMeta()) {
            // Update length and mtime on the logical file to be consistent with the physical file.
            // The linker relies on these fields to detect file changes.
            IPhysicalFile pf = _ds.getOA_(k.soid()).caNullable(k.kidx()).physicalFile();

            long mtime = pf.getLastModificationOrCurrentTime_();

            // We are about to set a null hash, which is allowed only on master branches. See Hasher
            // for detail.
            assert k.kidx().equals(KIndex.MASTER) : k;
            _ds.setCA_(k.sokid(), pf.getLength_(), mtime, null, t);
        }

        _updated.add(k);
        _dsNewUpdateMessage.schedule_();
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.migration;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This class implements cross-store movement of file contents and child stores.
 *
 * Cross store movement is implemented as a create+delete sequence in the logical file system but
 * the physical files should be moved an retain sufficient version information to preserve causal
 * relationships after cross-store move and avoid false content conflicts.
 */
public abstract class ImmigrantDetector
{
    static final Logger l = Loggers.getLogger(ImmigrantDetector.class);

    protected DirectoryService _ds;
    private NativeVersionControl _nvc;
    private ImmigrantVersionControl _ivc;
    protected IPhysicalStorage _ps;

    protected void baseInject_(DirectoryService ds, NativeVersionControl nvc,
            ImmigrantVersionControl ivc, IPhysicalStorage ps)
    {
        _ds = ds;
        _nvc = nvc;
        _ivc = ivc;
        _ps = ps;
    }

    /**
     * Detect and perform remotely-initiated migration
     *
     * On singleuser systems, there is an invariant that a given OID can only be admitted in one
     * store at any given time. When creating a new object, we check whether an object with the same
     * OID exists in another store, in which case we migrate content and version information from
     * that object and delete it to preserve the invariant.
     *
     * NB: this "move-based" approach is problematic in a TeamServer context where this invariant
     * cannot be safely enforced. For that reason, remotely-initiated migration is completely
     * disabled on TeamServer which has the unfortunate side-effect of causing unnecessary transfers
     * In theory a "copy-based" approach could allow migration support on the TeamServer but it is
     * not considered import enough at this time to warrant the investment (01/05/2013)
     *
     * @pre
     * 1) the destination is admitted
     * 2) permissions have been checked
     * 3) the destination metadata has been created
     * 4) no content exists in the destination object if it is a file
     * 5) no child store exists in the destination object if it is an anchor
     *
     * @param oaTo the OA of the destination object
     * @return true if immigration has been performed
     */
    public abstract boolean detectAndPerformImmigration_(@Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws Exception;

    /**
     * Perform locally-initiated migration
     *
     * @pre
     * 1) the source exists and is admitted
     * 2) the destination exists and is admitted
     * 3) the destination metadata has been created
     * 4) no content exists in the destination object if it is a file
     */
    public void immigrateFile_(@Nonnull OA oaFrom, @Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExNotFound
    {
        checkArgument(oaFrom.isFile() && oaTo.isFile());
        checkArgument(oaFrom.soid().oid().equals(oaTo.soid().oid()));
        checkArgument(!oaFrom.isExpelled() && !oaTo.isExpelled());

        ResolvedPath pathFrom = _ds.resolve_(oaFrom);
        ResolvedPath pathTo = _ds.resolve_(oaTo);

        SOCID socidFrom = new SOCID(oaFrom.soid(), CID.CONTENT);
        SOCID socidTo = new SOCID(oaTo.soid(), CID.CONTENT);

        // get the kml version before updating local versions to avoid assertion
        // failure in getKMLVersion_()
        Version vKMLToOld = _nvc.getKMLVersion_(socidTo);

        l.debug("migrate file {} -> {}", oaFrom, oaTo);

        Version vLocalSum = Version.empty();
        for (Entry<KIndex, CA> en : oaFrom.cas().entrySet()) {
            KIndex kidx = en.getKey();
            CA caFrom = en.getValue();
            SOCKID kFrom = new SOCKID(socidFrom, kidx);
            Version vFrom = _nvc.getLocalVersion_(kFrom);
            ContentHash hFrom = _ds.getCAHash_(kFrom.sokid());

            l.debug("migrate do " + kFrom);

            SOCKID kTo = new SOCKID(socidTo, kidx);

            // set content attribute
            _ds.createCA_(kTo.soid(), kidx, t);
            _ds.setCA_(kTo.sokid(), caFrom.length(), caFrom.mtime(), hFrom, t);

            // set local version
            _nvc.addLocalVersion_(kTo, vFrom, t);
            vLocalSum = vLocalSum.add_(vFrom);

            // move physical files
            _ps.newFile_(pathFrom, kidx).move_(pathTo, kidx, op, t);

            // TODO send NEW_UPDATE-like messages for migrated branches, but
            // only from the initiating peer of the migration. this will speed
            // up propagation of branches for peers that don't have the source
            // store. peers that do will apply the branches by the migration
            // process
        }

        // update kml version
        Version vKMLToDel = vKMLToOld.shadowedBy_(vLocalSum);
        _nvc.deleteKMLVersion_(socidTo, vKMLToDel, t);

        _ivc.createLocalImmigrantVersions_(socidTo, vLocalSum, t);
    }
}

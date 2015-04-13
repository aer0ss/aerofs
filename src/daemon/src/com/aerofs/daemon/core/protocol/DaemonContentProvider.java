package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.analytics.AnalyticsEventCounter;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;

public class DaemonContentProvider implements ContentProvider {
    private static final Logger l = Loggers.getLogger(DaemonContentProvider.class);

    private DirectoryService _ds;
    private IPhysicalStorage _ps;
    private ContentChangesDatabase _ccdb;
    private final AnalyticsEventCounter _conflictCounter;


    @Inject
    public DaemonContentProvider(DirectoryService ds, IPhysicalStorage ps,
                                 ContentChangesDatabase ccdb, Analytics analytics)
    {
        _ds = ds;
        _ps = ps;
        _ccdb = ccdb;
        _conflictCounter = new AnalyticsEventCounter(analytics) {
            @Override
            public IAnalyticsEvent createEvent(int count)
            {
                return new AnalyticsEvents.FileConflictEvent(count);
            }
        };
    }

    @Override
    public KIndex pickBranch(SOID soid) throws SQLException, ExNotFound, ExUpdateInProgress {
        OA oa = _ds.getOANullable_(soid);
        if (oa == null || oa.cas().isEmpty()) throw new ExNotFound();

        checkState(oa.cas().size() <= 2);
        // serve conflict branch preferentially
        //  - it cannot have local changes
        //  - it always dominates the base version from which the local MASTER diverged
        KIndex kidx = new KIndex(oa.cas().size() - 1);

        // TODO: in some cases it might be acceptable to transfer un-acked local changes
        // if some care is given to versioning and hashing

        // NB: only MASTER can have local changes
        if (kidx.isMaster() && _ccdb.hasChange_(soid.sidx(), soid.oid())) {
            l.debug("{} has local change", soid);
            throw new ExUpdateInProgress();
        }

        return kidx;
    }

    @Override
    public SendableContent content(SOKID k) throws SQLException, ExNotFound {
        OA oa = _ds.getOA_(k.soid());
        CA ca = oa.caThrows(k.kidx());
        long mtime = ca.mtime();
        // N.B. this is the length of the complete file contents, regardless of whether we're
        // skipping prefixLen bytes at the beginning of the content or not.
        long fileLength = ca.length();
        IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), k.kidx());

        checkState(mtime >= 0, "%s %s %s", k, oa, mtime);
        return new SendableContent(k, mtime, fileLength, _ds.getCAHash_(k), pf);
    }

    @Override
    public IPhysicalFile fileWithMatchingContent(SOID soid, ContentHash h)
            throws SQLException, ExNotFound {
        KIndex k = findBranchWithMatchingContent_(soid, h);
        return k != null ? _ps.newFile_(_ds.resolve_(soid), k) : null;
    }


    /**
     * If the remote peer resolved a conflict and we're getting that update, chances are
     * one of our branches has the same content. Find that branch.
     *
     * @param object     The object whose branches to search
     * @param h The hash of the remote object's content
     * @return The branch with the same content as the remote, or null
     */
    private @Nullable KIndex findBranchWithMatchingContent_(SOID object, @Nonnull ContentHash h)
            throws ExNotFound, SQLException {
        // See if we have the same content in one of our branches
        for (KIndex branch : _ds.getOAThrows_(object).cas().keySet()) {
            SOKID branchObject = new SOKID(object, branch);
            ContentHash localHash = _ds.getCAHash_(branchObject);
            if (localHash != null && localHash.equals(h)) {
                return branch;
            }
        }
        return null;
    }

    @Override
    public void apply_(IPhysicalPrefix prefix, IPhysicalFile pf, long replyMTime, ContentHash h, Trans t)
            throws Exception
    {
        // TODO(phoenix): validate prefix
        // lookup expected size and hash for the given version in RemoteContentDatabase

        // get length of the prefix before the actual transaction.
        long len = prefix.getLength_();

        // can't use the old values as the attributes might have changed
        // during pauses, due to aliasing and such
        SOKID k = pf.sokid();
        OA oa = _ds.getOAThrows_(k.soid());

        // abort if the object is expelled. Although Download.java checks
        // for this condition before starting the download, but the object
        // may be expelled during pauses of the current thread.
        if (oa.isExpelled()) {
            prefix.delete_();
            throw new ExAborted("expelled " + k);
        }

        CA ca = oa.caNullable(k.kidx());
        boolean wasPresent = ca != null;
        if (wasPresent && pf.wasModifiedSince(ca.mtime(), ca.length())) {
            // the linked file modified via the local filesystem
            // (i.e. the linker), but the linker hasn't received
            // the notification yet. we should not overwrite the
            // file in this case otherwise the local update will get
            // lost.
            //
            // BUGBUG NB there is still a tiny time window between the
            // test above and the apply_() below that the file is
            // updated via the filesystem.
            pf.onUnexpectedModification_(ca.mtime());
            throw new ExAborted(k + " has changed locally: expected=("
                    + ca.mtime() + "," + ca.length() + ") actual=("
                    + pf.lastModified() + "," + pf.lengthOrZeroIfNotFile() + ")");
        }

        if (replyMTime < 0) throw new ExProtocolError("negative mtime");
        long mtime = _ps.apply_(prefix, pf, wasPresent, replyMTime, t);

        if (!wasPresent) {
            if (!k.kidx().equals(KIndex.MASTER)) {
                // record creation of conflict branch here instead of in DirectoryService
                // Aliasing and Migration may recreate them and we only want to record each
                // conflict once
                _conflictCounter.inc();
            }
            _ds.createCA_(k.soid(), k.kidx(), t);
        }
        _ds.setCA_(k, len, mtime, h, t);
    }
}

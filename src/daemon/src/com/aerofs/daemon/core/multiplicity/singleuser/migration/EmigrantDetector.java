package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.EmigrantUtil;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkArgument;

public class EmigrantDetector implements IEmigrantDetector
{
    static final Logger l = Loggers.getLogger(EmigrantDetector.class);

    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public EmigrantDetector(DirectoryService ds, IMapSID2SIndex sid2sidx)
    {
        _sid2sidx = sid2sidx;
        _ds = ds;
    }

    @Override
    public void detectAndPerformEmigration_(SOID soid, OID oidParentTo, String nameTo,
            List<ByteString> sidsEmigrantTargetAncestor, IDownloadContext cxt)
            throws Exception
    {
        if (!shouldMigrate_(soid, oidParentTo, nameTo, sidsEmigrantTargetAncestor))  return;

        SID sidTo = EmigrantUtil.getEmigrantTargetSID(nameTo);
        checkArgument(sidTo != null, nameTo);
        l.debug("emigration detected {}=>{}", soid, sidTo);

        SIndex sidxTo = downloadTargetAndAncestorStores_(sidsEmigrantTargetAncestor, cxt, sidTo);
        if (sidxTo == null) return;

        emigrate(soid, cxt, sidxTo);
    }

    private void emigrate(SOID soid, IDownloadContext cxt, SIndex sidxTo)
            throws Exception
    {
        OA oa = _ds.getOA_(soid);

        if (oa.isAnchor() || oa.isFile()) {
            // Download the anchor or file's metadata into the target store to trigger the
            // immigration process. If it's an anchor, moving the anchor causes the entire store
            // under the anchor to be moved.
            //
            SOCID socidTo = new SOCID(sidxTo, soid.oid(), CID.META);
            l.debug("dl immigrant {}", socidTo.soid());
            cxt.downloadSync_(socidTo, DependencyType.UNSPECIFIED);

        } else {
            checkArgument(oa.isDir(), "%s %s", oa.soid(), oa.type());

            // Comment (A), referred to by ObjectDeleter.deleteAndEmigrate_().
            //
            // if it's a folder, try to move out children before deleting it (by this method's
            // caller). There are two cases where a non-empty folder can receive an emigrating
            // update:
            // 1. the children under the folder have been emigrated or moved to other folder by the
            //      peer who initiated the folder migration, but the local peer hasn't received
            //      their updates.
            // 2. the local peer has added new objects under the folder at the same time the folder
            //      was emigrated by the other peer.
            //
            // the following code can address the first but not the second case, which causes the
            // new objects to be incorrectly deleted (into Sync History). This is a bug and will be
            // fixed after we implement fine-grained download error control, so that we can use the
            // "object not found" error to indicate which objects don't exist on the remote peer.
            //
            for (OID oidChild : _ds.getChildren_(soid)) {
                SOCID socidChild = new SOCID(soid.sidx(), oidChild, CID.META);
                try {
                    cxt.downloadSync_(socidChild, DependencyType.UNSPECIFIED);
                } catch (Exception e) {
                    // it might be a false alarm, as the child may have been downloaded and migrated
                    // before the downloadSync above started the download thread.
                    l.debug("emigration child dl {}, bug ENG-1287: {}", socidChild, Util.e(e));
                }
            }
        }
    }

    private boolean shouldMigrate_(SOID soid, OID oidParentTo, String nameTo,
            List<ByteString> sidsEmigrantTargetAncestor)
            throws SQLException
    {
        // do not not migrate if the remote peer doesn't have the target store
        if (sidsEmigrantTargetAncestor.isEmpty()) return false;

        // do not migrate if it's not an emigrant
        // TODO: check isDeleted_ ? (if so, in which store?)
        if (!EmigrantUtil.isEmigrantName(nameTo) || !oidParentTo.isTrash()) return false;

        // do not migrate if the object doesn't exist (no emigration is needed)
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return false;

        // do not migrate if the object has been migrated before
        // TODO: check isDeleted_ ?
        return !(EmigrantUtil.isEmigrantName(oa.name()) && oa.parent().isTrash());
    }

    /**
     * Download the emigrant's target store and its ancestors (i.e. their anchors) as necessary.
     */
    private SIndex downloadTargetAndAncestorStores_(List<ByteString> sidsEmigrantTargetAncestor,
            IDownloadContext cxt, SID sidTarget)
            throws Exception
    {
        Queue<SID> sids = new ArrayDeque<>(sidsEmigrantTargetAncestor.size() + 1);
        sids.add(sidTarget);
        for (ByteString bstr : sidsEmigrantTargetAncestor) {
            SID sidAncestor = new SID(BaseUtil.fromPB(bstr));
            // sid == sidAncestor if the emigrant object is under the root store of the remote peer
            if (!sidTarget.equals(sidAncestor)) sids.add(sidAncestor);
        }

        SIndex sidx = downloadAncestorStores_(sids, cxt);
        if (sidx != null) {
            checkArgument(sidx.equals(_sid2sidx.get_(sidTarget)), "%s %s", sidx, sidTarget);
        }

        return sidx;
    }

    /**
     * recursively walk up from the direct parent store until reaching a common
     * store shared by both the local and remote peer, and then walk down to
     * download each of them one by one.
     *
     * @return the ancestor store being downloaded. null if the local and remote
     * peers don't share common ancestors
     */
    private @Nullable SIndex downloadAncestorStores_(Queue<SID> sids, IDownloadContext cxt)
            throws Exception
    {
        if (sids.isEmpty()) return null;

        SID sid = sids.remove();

        // If there already exists an SIndex for sid, the store exists locally
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx != null) return sidx;

        SIndex sidxAnchor = downloadAncestorStores_(sids, cxt);
        if (sidxAnchor == null) return null;

        SOID soidAnchor = new SOID(sidxAnchor, SID.storeSID2anchorOID(sid));
        l.debug("download ancestor anchor " + soidAnchor);

        cxt.downloadSync_(new SOCID(soidAnchor, CID.META), DependencyType.PARENT);

        // may return null even after downloading of the anchor succeeds.
        // for example, the remote peer may delete the anchor after the meta
        // is sent and before the local peer requests for the anchor.
        return _sid2sidx.getNullable_(sid);
    }

}

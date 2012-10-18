package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.Downloads;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.net.To.Factory;
import com.aerofs.daemon.core.net.dependence.ParentDependencyEdge;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class EmigrantDetector
{
    static final Logger l = Util.l(EmigrantDetector.class);

    private final DirectoryService _ds;
    // This class uses the Downloads.downloadSync_ method to get dependent immigrant/emmigrant
    // objects and ancestors. It uses these methods directly, instead of the ExDependsOn pattern
    // to avoid the delay in reprocessing a ComponentReply, once the required object has been
    // downloaded locally.
    private final Downloads _dls;
    private final To.Factory _factTo;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public EmigrantDetector(Factory factTo, Downloads dls, DirectoryService ds,
            IMapSID2SIndex sid2sidx)
    {
        _sid2sidx = sid2sidx;
        _factTo = factTo;
        _dls = dls;
        _ds = ds;
    }

    /**
     * call this method before applying a metadata update from a remote peer,
     * so that in case of a deletion update, the content of the object or of the
     * object's children, are emigrated instead of deleted because of the update.
     *
     * NB. this method might pause to download anchoring stores or children objects.
     *
     * @param soid the object being downloaded. must be a metadata download
     * @param oidParentTo the parent oid that the object is going to move to
     * @param nameTo the name that the object is going to use
     * @param sidsEmigrantTargetAncestor the value of the emigrant_target_ancestor_sid
     * field from PBMeta
     * @param did the device that provided the update
     */
    public void detectAndPerformEmigration_(SOID soid, OID oidParentTo,
            String nameTo, List<ByteString> sidsEmigrantTargetAncestor, DID did,
            Token tk)
            throws Exception
    {
        // do nothing if the remote peer doesn't have the target store
        if (sidsEmigrantTargetAncestor.isEmpty()) return;

        // do nothing if it's not an emigrant
        // TODO: check isDeleted_ ? (if so, in which store?)
        if (!EmigrantCreator.isEmigrantName(nameTo) || !oidParentTo.isTrash()) return;

        // do nothing if the object doesn't exist (no emigration is needed)
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return;

        // do nothing if the object has been migrated before
        // TODO: check isDeleted_ ?
        if (EmigrantCreator.isEmigrantName(oa.name()) && oa.parent().isTrash()) return;

        SID sidTo = EmigrantCreator.getEmigrantTargetSID(nameTo);
        assert sidTo != null : nameTo;
        l.info("emigration detected " + oa.type() + " " + soid + "->" + sidTo);

        // download the store (i.e. their anchors) and its ancestors as necessary
        Queue<SID> sids = new ArrayDeque<SID>(sidsEmigrantTargetAncestor.size() + 1);
        sids.add(sidTo);
        for (ByteString bstr : sidsEmigrantTargetAncestor) {
            SID sidAncestor = new SID(bstr);
            // sid == sidAncestor if the emigrant object is under the root store of the remote peer
            if (!sidTo.equals(sidAncestor)) sids.add(sidAncestor);
        }

        SOCID socidFrom = new SOCID(soid, CID.META);
        SIndex sidxTo = downloadEmigrantAncestorStores_(sids, did, tk, soid);
        if (sidxTo == null) return;
        assert sidxTo.equals(_sid2sidx.get_(sidTo));

        // Now the target store is in place. Emigrate the object.
        // If the object is an anchor, moving the object causes the entire store under the anchor
        // to be moved as well.
        if (!oa.isDir()) {
            SOCID socidTo = new SOCID(sidxTo, soid.oid(), CID.META);
            l.info("dl immigrant " + socidTo.soid());
            _dls.downloadSync_(socidTo, _factTo.create_(did), tk, socidFrom);
        } else {
            // Comment (A), referred to by ObjectDeletion.delete_().
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
            // new objects to be incorrectly deleted (into the revision area). this is a bug and
            // will be fixed after we implement fine-grained download error control, so that we can
            // use the "object not found" error to indicate which objects don't exist on the remote
            // peer.
            //
            for (OID oidChild : _ds.getChildren_(soid)) {
                SOCID socidChild = new SOCID(soid.sidx(), oidChild, CID.META);
                try {
                    _dls.downloadSync_(socidChild, _factTo.create_(did), tk, socidFrom);
                } catch (Exception e) {
                    // it might be a false alarm, as the child may have been downloaded and migrated
                    // before the downloadSync above started the download thread.
                    l.info("emigration child dl " + socidChild + ", bug aerofs-165: " + Util.e(e));
                }
            }
        }
    }

    /**
     * recursively walk up from the direct parent store until reaching a common
     * store shared by both the local and remote peer, and then walk down to
     * download each of them one by one.
     *
     * @return the ancestor store being downloaded. null if the local and remote
     * peers don't share common ancestors
     */
    private @Nullable SIndex downloadEmigrantAncestorStores_(Queue<SID> sids, DID did, Token tk,
            SOID soid)
            throws Exception
    {
        SID sid = sids.poll();
        if (sid == null) return null;  // end of the queue

        // If there already exists an SIndex for sid, the store exists locally
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx != null) return sidx;

        SOCID socid = new SOCID(soid, CID.META);
        SIndex sidxAnchor = downloadEmigrantAncestorStores_(sids, did, tk, soid);
        if (sidxAnchor == null) return null;

        SOID soidAnchor = new SOID(sidxAnchor, SID.storeSID2anchorOID(sid));
        l.info("download ancestor anchor " + soidAnchor);
        ParentDependencyEdge dependency = new ParentDependencyEdge(socid, new SOCID(soidAnchor, CID.META));
        _dls.downloadSync_(dependency, _factTo.create_(did), tk);

        // may return null even after downloading of the anchor succeeds.
        // for example, the remote peer may delete the anchor after the meta
        // is sent and before the local peer requests for the anchor.
        return _sid2sidx.getNullable_(sid);
    }

}

package com.aerofs.daemon.core.alias;

import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.proto.PrefixVersionControl;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.security.DigestException;
import java.sql.SQLException;
import java.util.Set;

/**
 * Helper class for Aliasing.java that helps in moving version vectors, content etc.
 * while performing aliasing operation.
 */
public class AliasingMover
{
    private static final Logger l = Util.l(AliasingMover.class);

    private final DirectoryService _ds;
    private final PrefixVersionControl _pvc;
    private final NativeVersionControl _nvc;
    private final Hasher _hasher;
    private final ObjectMover _om;
    private final BranchDeleter _bd;

    @Inject
    public AliasingMover(DirectoryService ds, Hasher hasher, ObjectMover om,
            PrefixVersionControl pvc, NativeVersionControl nvc, BranchDeleter bd)
    {
        _ds = ds;
        _hasher = hasher;
        _om = om;
        _pvc = pvc;
        _nvc = nvc;
        _bd = bd;
    }

    /**
     * Updates KML version of the target as per the following formula:
     * vKMLAlias = null
     * vKMLTarget = (vKMLTarget U vKMLAlias) - vAllLocalTarget - vAllLocalAlias
     *
     * The last step, "- vAllLocalAlias", is to subtract alias's local versions from target's KML
     * version, so we can move alias's local versions to target's local version later without
     * causing a version to appear in both local and KML versions of the target.
     *
     * Note that both vAllLocalAlias and vAllLocalTarget may refer to future values rather than the
     * values currently stored on the database.
     */
    public void moveKMLVersion_(SOCID alias, SOCID target, Version vAllLocalAlias,
            Version vAllLocalTarget, Trans t) throws SQLException
    {
        assert !alias.equals(target);

        Version vKMLAlias =  _nvc.getKMLVersion_(alias).withoutAliasTicks_();
        Version vKMLTarget = _nvc.getKMLVersion_(target);
        Version vKMLAlias_AllLocalTarget = vKMLAlias.sub_(vAllLocalTarget);
        Version vKMLAddToTarget = vKMLAlias_AllLocalTarget.sub_(vKMLTarget);
        _nvc.addKMLVersionAndCollectorSequence_(target, vKMLAddToTarget, t);

        // TODO (WW) For debugging only. To be removed
        l.warn(alias + "->" + target + " " + vAllLocalAlias + " " + vAllLocalTarget + " " +
            " " + _nvc.getKMLVersion_(target) + " " + vKMLAlias + " " + vKMLTarget + " " +
                vKMLAlias_AllLocalTarget + " " + vKMLAddToTarget);

        // Perform the last step, vKMLTarget -= vAllLocalAlias. see method header comment.
        Version vKMLTargetNew = vKMLTarget.add_(vKMLAlias_AllLocalTarget);
        assert vKMLTargetNew.equals(_nvc.getKMLVersion_(target)) :
                alias + " " + target + " " + vAllLocalAlias + " " + vAllLocalTarget + " " +
                vKMLTargetNew + " " + _nvc.getKMLVersion_(target) + " " +
                vKMLAlias + " " + vKMLTarget + " " + vKMLAlias_AllLocalTarget + " " +
                vKMLAddToTarget;

        // add_(vAllLocalTarget) is necessary since vAllLocalTarget may reflect the future value,
        // and the current local version of the target may not contain the elements that should be
        // removed from the new KML of the target. This operation is needed to satisfy the
        // invariant that KML should not be shadowed by local versions.
        Version vKMLDelFromTarget = vKMLTargetNew.shadowedBy_(vAllLocalAlias.add_(vAllLocalTarget));
        _nvc.deleteKMLVersion_(target, vKMLDelFromTarget, t);

        // Delete alias KML
        _nvc.deleteKMLVersionPermanently_(alias, vKMLAlias, t);
    }

    /**
     * Move the content component of the alias as well as its versions to the target object.
     */
    public void moveContent_(SOCID alias, SOCID target, Trans t)
        throws SQLException, ExNotFound, ExAborted, DigestException, IOException
    {
        assert alias.cid() == CID.CONTENT && target.cid() == CID.CONTENT;
        assert alias.sidx().equals(target.sidx());

        // Ordered set of KIndices useful for comparison of branches below.
        // TODO because SortedMap is a Map, its keyset is simply a Set, but we know it's
        // actually sorted. See Comment A at the end of this method for a more robust solution.
        Set<KIndex> kidxsTarget = _ds.getOA_(target.soid()).cas().keySet();
        Set<KIndex> kidxsAlias = _ds.getOA_(alias.soid()).cas().keySet();

        Version vAllLocalTarget = _nvc.getAllLocalVersions_(target);
        Version vAllLocalAlias = _nvc.getAllLocalVersions_(alias);

        // KML version needs to be moved before local version.
        moveKMLVersion_(alias, target, vAllLocalAlias, vAllLocalTarget, t);

        // Move local version of the content.
        //
        // A branch of alias object should first be compared with MASTER branch of target object
        // so that corresponding branch of alias object is moved to MASTER branch of target object
        // when hashes match. Hence iterate over ordered set of KIndices.

        if (!_ds.getOA_(target.soid()).isExpelled()) {
            moveBranches_(alias, target, kidxsAlias, kidxsTarget, t);

        } else {
            // The target object is expelled.

            // TODO change it to info log
            l.warn("target expelled on " + alias + "->" + target);

            // Add all the local versions from the alias object as KML version.
            Version vKMLTarget = _nvc.getKMLVersion_(target);
            // this is equivalent to ... = vAliasAllLocal.sub_(_nvc.getAllVersions_(target);
            Version vKMLAddToTarget = vAllLocalAlias.sub_(vAllLocalTarget).sub_(vKMLTarget);
            // Don't need to add to collector sequence as the target is expelled. The KML will be
            // moved back to the collector sequence when the target is re-admitted.
            _nvc.addKMLVersion_(target, vKMLAddToTarget, t);

            // Delete all the branches and their versions associated of the alias object.
            for (KIndex kidxAlias : kidxsAlias) {
                SOCKID k = new SOCKID(alias, kidxAlias);
                deleteBranchAndPrefix_(k, _nvc.getLocalVersion_(k), true, t);
            }

            // TODO trigger expulsion listener for the alias object?
        }
    }

    /**
     * Move all the branches and their versions from the alias object to the target.
     * A branch of alias object will deleted if there is a branch with same content hash on the
     * target. Otherwise it's moved to a new branch on the target.
     */
    private void moveBranches_(SOCID alias, SOCID target, Set<KIndex> kidxsAlias,
            Set<KIndex> kidxsTarget, Trans t)
            throws SQLException, ExNotFound, ExAborted, DigestException, IOException
    {
        assert alias.cid() == CID.CONTENT && target.cid() == CID.CONTENT;

        // TODO FIXME
        // It seems that the only reason to force the type of kIndicesTarget to be a
        // SortedMap is so that when iterating over branches (kindices) you deal
        // with MASTER first, then the other conflict branches after. You don't
        // actually care what order you address the conflict branches. This smells
        // bad, as we have conveniently chosen MASTER to be the lowest branch value,
        // but that doesn't have to be the case. So using a SortedMap only works
        // because MASTER is the minimum. If it is necessary that MASTER is handled
        // first, then should that branch get its own special handling, followed by
        // the conflict branches (in arbitrary order)? Or alternatively construct a
        // list, putting MASTER at the front, then the rest of the branches after?

        int kidxMax = KIndex.MASTER.getInt() - 1;
        for (KIndex kidxAlias : kidxsAlias) {
            SOCKID kAlias = new SOCKID(alias, kidxAlias);
            Version vAlias = _nvc.getLocalVersion_(kAlias);
            boolean match = false;

            // avoid calls to computeHashBlocking as much as possible as it is expensive and blocks
            // the entire core.
            ContentHash hAlias = _ds.getCAHash_(kAlias.sokid());

            // Content is moved from alias to target object within nested loop which may change
            // KIndices on the target object. Therefore, it's necessary to query the KIndices of
            // target object _before_ starting the outer loop.
            for (KIndex kidxTarget : kidxsTarget) {
                kidxMax = Math.max(kidxTarget.getInt(), kidxMax);
                SOCKID kTarget = new SOCKID(target, kidxTarget);
                ContentHash hTarget = _hasher.computeHashBlocking_(kTarget.sokid());

                if (hAlias == null) hAlias = _hasher.computeHashBlocking_(kAlias.sokid());

                if (hAlias.equals(hTarget)) {
                    match = true;
                    // Merge alias branch with existing target branch.
                    Version vTarget = _nvc.getLocalVersion_(kTarget);
                    Version vMove = vAlias.sub_(vTarget);
                    _nvc.addLocalVersion_(kTarget, vMove, t);
                    break;
                }
            }

            // Create a new branch on target when no match is found.
            if (!match) {
                KIndex kidxTarget = new KIndex(++kidxMax);

                l.warn("almov " + new SOCKID(alias, kidxAlias) + "->" +
                        new SOCKID(target, kidxTarget));

                // Compute hash only if the branch is a non-master branch on the target, since
                // DS.setCA_() requires non-null hashes on these branches. See Hasher for detail.
                // The computation on the alias must be done _before_ moving the physical file to
                // the target.
                if (hAlias == null && !kidxTarget.equals(KIndex.MASTER)) {
                    hAlias = _hasher.computeHashBlocking_(kAlias.sokid());
                }

                // Create CA for the new branch on the target.
                _ds.createCA_(target.soid(), kidxTarget, t);

                CA caFrom = _ds.getOA_(alias.soid()).ca(kidxAlias);
                CA caTo = _ds.getOA_(target.soid()).ca(kidxTarget);
                caFrom.physicalFile().move_(caTo.physicalFile(), PhysicalOp.APPLY, t);

                SOCKID kTarget = new SOCKID(target, kidxTarget);
                _ds.setCA_(kTarget.sokid(), caFrom.length(), caFrom.mtime(), hAlias, t);
                _nvc.addLocalVersion_(kTarget, vAlias, t);
            }

            // Alias content branch should be deleted since it's either merged with existing target
            // branch or a new branch was created on the target. Also delete physical file if
            // matching file was found
            deleteBranchAndPrefix_(kAlias, vAlias, match, t);
        }
    }

    /**
     * Delete the specified branch and its version. Also delete the prefix associated with the
     * branch.
     */
    private void deleteBranchAndPrefix_(SOCKID k, Version v, boolean delPhysicalFile, Trans t)
            throws SQLException, ExNotFound, IOException
    {
        _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);
        _bd.deleteBranch_(k, v, delPhysicalFile, true, t);
    }

    /**
     * Move children from alias directory to the target directory.
     * If a name conflict is encountered while moving then the file/sub-directory is renamed.
     */
    public void moveChildrenFromAliasToTargetDir_(SIndex sidx, SOID alias, SOID target, Trans t)
        throws Exception
    {
        assert _ds.getOA_(alias).isDir();
        assert _ds.getOA_(target).isDir();

        Path targetPath = _ds.resolve_(target);

        // Files/dirs directly under the target dir will retain their names, whereas files/dirs
        // directly under the alias dir are renamed to avoid name conflict
        for (OID oid : _ds.getChildren_(alias)) {
            SOID child = new SOID(sidx, oid);
            String childName = _ds.getOA_(child).name();

            // We don't currently gracefully handle moving when the OIDs are equal
            assert !child.equals(target) : sidx + " " + alias + " " + target + " " + child;

            // Look for a name conflict before moving the child. Update its version only if renaming
            // is required due to name conflict.
            boolean updateVersion = false;
            if (_ds.resolveNullable_(targetPath.append(childName)) != null) {
                childName = _ds.generateNameConflictFileName_(targetPath, childName);
                updateVersion = true;
            }

            _om.moveInSameStore_(child, target.oid(), childName, PhysicalOp.APPLY, false,
                    updateVersion, t);
        }
    }

    /**
     * Merges the meta-data local versions according to the following formula:
     * vMerged = vAlias U vTarget
     */
    public void moveMetadataLocalVersion_(SOCID alias, SOCID target, Version vAlias,
            Version vTarget, Trans t) throws SQLException
    {
        assert alias.cid().isMeta();
        assert target.cid().isMeta();

        // vTarget isn't necessarily version present locally, hence it's necessary to query local
        // target version before merging.
        Version vTargetLocal = _nvc.getLocalVersion_(new SOCKID(target));
        Version vMerged = vTarget.add_(vAlias);
        Version vMerged_TargetLocal = vMerged.sub_(vTargetLocal);

        _nvc.addLocalVersion_(new SOCKID(target), vMerged_TargetLocal, t);

        // Since version of alias has been moved to target, remove version of alias object.
        deleteAliasVersion_(alias, vAlias, t);

        assert vMerged.equals(_nvc.getLocalVersion_(new SOCKID(target)));
    }

    /**
     * Delete local version of the alias object.
     *
     * @param vAliasDel Local version to be deleted from the alias object.
     */
    private void deleteAliasVersion_(SOCID alias, Version vAliasDel, Trans t)
        throws SQLException
    {
        // vAliasDel isn't necessarily version present locally, hence it's necessary to query
        // local alias version before deleting.
        Version vAliasLocal = _nvc.getLocalVersion_(new SOCKID(alias));
        Version vAliasLocal_AliasDel = vAliasLocal.sub_(vAliasDel);
        Version vDel = vAliasLocal.sub_(vAliasLocal_AliasDel);

        _nvc.deleteLocalVersionPermanently_(new SOCKID(alias), vDel, t);
    }
}

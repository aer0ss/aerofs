package com.aerofs.daemon.core.alias;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.protocol.PrefixVersionControl;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.security.DigestException;
import java.sql.SQLException;
import java.util.Set;
import java.util.SortedMap;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Helper class for Aliasing.java that helps in moving version vectors, content etc.
 * while performing aliasing operation.
 */
public class AliasingMover
{
    private static final Logger l = Loggers.getLogger(AliasingMover.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final ObjectSurgeon _os;
    private final PrefixVersionControl _pvc;
    private final NativeVersionControl _nvc;
    private final Hasher _hasher;
    private final ObjectMover _om;
    private final BranchDeleter _bd;
    private final Expulsion _expulsion;

    @Inject
    public AliasingMover(DirectoryService ds, ObjectSurgeon os, Hasher hasher, ObjectMover om,
            Expulsion expulsion, PrefixVersionControl pvc, NativeVersionControl nvc,
            BranchDeleter bd, IPhysicalStorage ps)
    {
        _ds = ds;
        _ps = ps;
        _os = os;
        _hasher = hasher;
        _om = om;
        _pvc = pvc;
        _nvc = nvc;
        _bd = bd;
        _expulsion = expulsion;
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
        assert !alias.equals(target) : alias;

        // TODO:FIXME this may lose ticks...
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
     *
     * @pre the target must have an OA (the alias may not)
     */
    public void moveContent_(SOID aliasObject, SOID targetObject, Trans t)
        throws SQLException, ExNotFound, ExAborted, DigestException, IOException
    {
        assert aliasObject.sidx().equals(targetObject.sidx());

        OA oaAlias = _ds.getOANullable_(aliasObject);
        OA oaTarget = _ds.getOA_(targetObject);

        SOCID alias = new SOCID(aliasObject, CID.CONTENT);
        SOCID target = new SOCID(targetObject, CID.CONTENT);

        Version vAllLocalAlias = _nvc.getAllLocalVersions_(alias);
        Version vAllLocalTarget = _nvc.getAllLocalVersions_(target);

        // KML version needs to be moved before local version.
        moveKMLVersion_(alias, target, vAllLocalAlias, vAllLocalTarget, t);

        // nothing to do, move along
        if (oaAlias == null) {
            l.info("remote alias: no content " + aliasObject);
            return;
        }

        Set<KIndex> kidxsAlias = oaAlias.cas().keySet();
        Set<KIndex> kidxsTarget = oaTarget.cas().keySet();

        if (!oaTarget.isExpelled()) {
            // move content from alias to target
            moveBranches_(alias, target, kidxsAlias, kidxsTarget, t);
        } else {
            // The target object is expelled.
            l.info("target expelled on " + aliasObject + "->" + targetObject);

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
     *
     * @pre both alias and target need to be locally present
     */
    private void moveBranches_(SOCID alias, SOCID target, Set<KIndex> kidxsAlias,
            Set<KIndex> kidxsTarget, Trans t)
            throws SQLException, ExNotFound, ExAborted, DigestException, IOException
    {
        assert alias.cid() == CID.CONTENT && target.cid() == CID.CONTENT;
        if (kidxsAlias.isEmpty()) {
            l.info("local alias: no content " + alias.soid());
            return;
        }
        // The alias MASTER branch is handled first to make sure it is moved into the target MASTER
        // branch if the target is empty and that regardless of the branch numbering
        assert kidxsAlias.contains(KIndex.MASTER) : kidxsAlias;

        moveBranch_(new SOCKID(alias, KIndex.MASTER), target, kidxsTarget, t);

        for (KIndex kidx : kidxsAlias) {
            if (kidx.equals(KIndex.MASTER)) continue;
            moveBranch_(new SOCKID(alias, kidx), target, kidxsTarget, t);
        }
    }

    private void moveBranch_(SOCKID alias, SOCID target, Set<KIndex> kidxsTarget, Trans t)
            throws SQLException, ExNotFound, ExAborted, DigestException, IOException
    {
        KIndex kidxAlias = alias.kidx();
        Version vAlias = _nvc.getLocalVersion_(alias);

        // avoid calls to computeHashBlocking as much as possible as it is expensive and blocks
        // the entire core.
        // TODO: only the MASTER hash may need to be computed, ideally that should be done in RAAU
        // in a way that allows us to release the core lock around the hashing...
        ContentHash hAlias = _ds.getCAHash_(alias.sokid());
        if (hAlias == null && !kidxsTarget.isEmpty()) {
            hAlias = _hasher.computeHashBlocking_(alias.sokid());
        }

        KIndex kidxMatch = findMatchingBranch_(target, kidxsTarget, hAlias);
        boolean match = kidxMatch != null;
        if (match) {
            // Merge alias branch with existing target branch.
            SOCKID kTarget = new SOCKID(target, kidxMatch);
            Version vTarget = _nvc.getLocalVersion_(kTarget);
            Version vMove = vAlias.sub_(vTarget);
            _nvc.addLocalVersion_(kTarget, vMove, t);
        } else {
            // Create a new branch on target when no match is found.
            SortedMap<KIndex, CA> targetCAs = _ds.getOA_(target.soid()).cas();
            KIndex kidxTarget = targetCAs.isEmpty() ? KIndex.MASTER
                    : targetCAs.lastKey().increment();

            l.warn("almov " + alias + "->" + new SOCKID(target, kidxTarget));

            // Compute hash only if the branch is a non-master branch on the target, since
            // DS.setCA_() requires non-null hashes on these branches. See Hasher for detail.
            // The computation on the alias must be done _before_ moving the physical file to
            // the target.
            if (hAlias == null && !kidxTarget.isMaster()) {
                hAlias = _hasher.computeHashBlocking_(alias.sokid());
            }

            // Create CA for the new branch on the target.
            _ds.createCA_(target.soid(), kidxTarget, t);

            OA oaFrom = _ds.getOA_(alias.soid());
            CA caFrom = oaFrom.ca(kidxAlias);
            OA oaTo = _ds.getOA_(target.soid());
            oaTo.ca(kidxTarget);

            _ps.newFile_(_ds.resolve_(oaFrom), kidxAlias)
                    .move_(_ds.resolve_(oaTo), kidxTarget, PhysicalOp.APPLY, t);

            SOCKID kTarget = new SOCKID(target, kidxTarget);
            _ds.setCA_(kTarget.sokid(), caFrom.length(), caFrom.mtime(), hAlias, t);
            _nvc.addLocalVersion_(kTarget, vAlias, t);
        }

        // Alias content branch should be deleted since it's either merged with existing target
        // branch or a new branch was created on the target. Also delete physical file if
        // matching file was found
        deleteBranchAndPrefix_(alias, vAlias, match, t);
    }

    private KIndex findMatchingBranch_(SOCID target, Set<KIndex> kidxsTarget, ContentHash hAlias)
            throws ExNotFound, SQLException, ExAborted, IOException, DigestException
    {
        // TODO: reorg branches in Map<ContentHash, KIndex> for nicer and faster code
        // NB: this would require enforcing the presence of a MASTER hash beforehand...

        // Content is moved from alias to target object within nested loop which may change
        // KIndices on the target object. Therefore, it's necessary to query the KIndices of
        // target object _before_ starting the outer loop.
        for (KIndex kidx : kidxsTarget) {
            ContentHash hTarget = _hasher.computeHashBlocking_(new SOKID(target.soid(), kidx));
            if (hAlias.equals(hTarget)) return kidx;
        }
        return null;
    }

    /**
     * Delete the specified branch and its version. Also delete the prefix associated with the
     * branch.
     */
    private void deleteBranchAndPrefix_(SOCKID k, Version v, boolean delPhysicalFile, Trans t)
            throws SQLException, IOException
    {
        _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);
        _bd.deleteBranch_(k, v, delPhysicalFile, true, t);
    }

    /**
     * Move children from alias directory to the target directory.
     * If a name conflict is encountered while moving then the file/sub-directory is renamed.
     *
     * @pre both alias and target need to be locally present
     */
    public void moveChildrenFromAliasToTargetDir_(SOID alias, SOID target, Trans t)
        throws Exception
    {
        SIndex sidx = alias.sidx();
        assert sidx.equals(target.sidx()) : sidx + " " + target.sidx();

        OA oaAlias = _ds.getOANullable_(alias);
        if (oaAlias == null) {
            l.info("remote alias: no children " + alias);
            return;
        }

        checkArgument(oaAlias.isDir());
        checkArgument(_ds.getOA_(target).isDir());

        final Path targetPath = _ds.resolve_(target);
        final Path aliasPath = _ds.resolve_(alias);
        if (targetPath.isStrictlyUnder(aliasPath)) {
            // If the target object is under the alias, we cannot move children of the alias
            // into the descendent target, lest horrible cycles result. Instead, swap the positions
            // of the target and alias OIDs in the logical directory tree, then move children of
            // the alias into the target folder
            _os.swapOIDsInSameStore_(sidx, alias.oid(), target.oid(), t);
        }

        // Files/dirs directly under the target dir will retain their names, whereas files/dirs
        // directly under the alias dir are renamed to avoid name conflict
        for (OID oid : _ds.getChildren_(alias)) {
            SOID child = new SOID(sidx, oid);
            String childName = _ds.getOA_(child).name();

            // We don't currently gracefully handle moving when the OIDs are equal
            assert !child.equals(target) : sidx + " " + alias + " " + target + " " + child;

            // Look for a name conflict before moving the child. Update its version only if renaming
            // is required due to name conflict.
            String newChildName = _ds.generateConflictFreeFileName_(targetPath, childName);
            boolean updateVersion = !newChildName.equals(childName);

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
        // vAlias isn't necessarily version present locally, hence it's necessary to query
        // local alias version before deleting.
        Version vAliasLocal = _nvc.getLocalVersion_(new SOCKID(alias));
        Version vDel = vAliasLocal.shadowedBy_(vAlias);
        _nvc.deleteLocalVersionPermanently_(new SOCKID(alias), vDel, t);

        assert vMerged.equals(_nvc.getLocalVersion_(new SOCKID(target)));
    }

    public void moveContentOrChildren_(SOID alias, SOID target, Trans t)
            throws Exception
    {
        Preconditions.checkState(alias.sidx().equals(target.sidx()));

        OA oaAlias = _ds.getOANullable_(alias);
        OA oaTarget = _ds.getOANullable_(target);

        if (oaAlias != null) {
            // if the alias was explicitly expelled we need to remove it from the table of expelled
            // objects to avoid problems in HdListExpelledObjects
            // if the target is remote, then we will propagate the expelled state from the alias
            // so we also need to add the target to the table of expelled objects to avoid inconsi-
            // stency between said table and the OA flags
            _expulsion.objectAliased_(oaAlias, oaTarget == null ? target : null, t);
        }

        if (oaTarget == null) {
            oaAlias = Preconditions.checkNotNull(oaAlias, alias + " " + target);
            l.info("remote target: fast path");

            // some physical storages use the OID to address objects so we need to
            // rename or we may end up in a broken state
            ResolvedPath src = _ds.resolve_(oaAlias);

            _os.replaceOID_(alias, target, t);

            if (oaAlias.isFile()) {
                SOCID cAlias = new SOCID(alias, CID.CONTENT);
                SOCID cTarget = new SOCID(target, CID.CONTENT);

                Version vAllLocalAlias = _nvc.getAllLocalVersions_(cAlias);
                Version vAllLocalTarget = _nvc.getAllLocalVersions_(cTarget);

                // KML version needs to be moved before local version.
                moveKMLVersion_(cAlias, cTarget, vAllLocalAlias, vAllLocalTarget, t);

                _pvc.deleteAllPrefixVersions_(alias, t);
                _nvc.moveAllLocalVersions_(cAlias, cTarget, t);
                for (KIndex kidx : oaAlias.cas().keySet()) {
                    _ps.newFile_(src, kidx).updateSOID_(target, t);
                }
            } else {
                _ps.newFolder_(src).updateSOID_(target, t);
            }
        } else {
            if (oaTarget.isDir()) {
                moveChildrenFromAliasToTargetDir_(alias, target, t);
                if (oaAlias != null && !oaAlias.isExpelled()) {
                    l.info("del alias dir {} {}", alias, Util.crc32(oaAlias.name()));
                    _ps.newFolder_(_ds.resolve_(oaAlias)).delete_(PhysicalOp.APPLY, t);
                }
            } else {
                moveContent_(alias, target, t);
            }

            // Alias is now moved to target. So remove the meta-data entry for alias.
            // Content was moved in moveContent_() method.
            if (oaAlias != null) _os.deleteOA_(alias, t);
        }
    }
}

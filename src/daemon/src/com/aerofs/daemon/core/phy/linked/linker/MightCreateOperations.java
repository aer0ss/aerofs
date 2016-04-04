/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import static com.aerofs.daemon.core.ds.OA.Type.DIR;
import static com.aerofs.daemon.core.ds.OA.Type.FILE;
import static com.aerofs.daemon.core.phy.PhysicalOp.APPLY;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class is in charge of executing whatever filesystem and db operations {@link MightCreate}
 * deems necessary to keep the mapping between logical and physical objects up-to-date
 */
class MightCreateOperations
{
    private static Logger l = Loggers.getLogger(MightCreateOperations.class);

    private final DirectoryService _ds;
    private final ObjectCreator _oc;
    private final ObjectMover _om;
    private final InjectableFile.Factory _factFile;
    private final SharedFolderTagFileAndIcon _sfti;
    private final CoreScheduler _sched;
    private final IMapSID2SIndex _sid2sidx;
    private final ImmigrantCreator _imc;
    private final HashQueue _hq;

    static enum Operation
    {
        // the following "core" operations are mutually exclusive
        CREATE,
        UPDATE,
        REPLACE,

        // the following "flags" can be combined with some of the above ops
        RENAME_TARGET,
        NON_REPRESENTABLE_TARGET,
        RANDOMIZE_SOURCE_FID;

        static private final EnumSet<Operation> FLAGS =
                EnumSet.of(RENAME_TARGET, NON_REPRESENTABLE_TARGET, RANDOMIZE_SOURCE_FID);
        static private final EnumSet<Operation> CORE =
                EnumSet.complementOf(FLAGS);

        // extract core operation (assume exactly one core operation is present)
        static private Operation core(Set<Operation> ops)
        {
            return Iterables.getOnlyElement(Sets.intersection(ops, Operation.CORE));
        }
    }

    @Inject
    public MightCreateOperations(DirectoryService ds, ObjectMover om, ObjectCreator oc,
            InjectableFile.Factory factFile, SharedFolderTagFileAndIcon sfti, CoreScheduler sched,
            IMapSID2SIndex sid2sidx, ImmigrantCreator imc, HashQueue hq)
    {
        _ds = ds;
        _oc = oc;
        _om = om;
        _imc = imc;
        _factFile = factFile;
        _sfti = sfti;
        _sched = sched;
        _sid2sidx = sid2sidx;
        _hq = hq;
    }

    /**
     * @return whether an object was created, replaced or renamed
     */
    public boolean executeOperation_(Set<Operation> ops, SOID sourceSOID, SOID targetSOID,
            PathCombo pc, FIDAndType fnt, IDeletionBuffer delBuffer, OIDGenerator og, Trans t)
            throws Exception
    {
        if (ops.contains(Operation.RANDOMIZE_SOURCE_FID)) assignRandomFID_(sourceSOID, t);

        if (ops.contains(Operation.RENAME_TARGET)) {
            PhysicalOp op = MAP;
            if (ops.contains(Operation.NON_REPRESENTABLE_TARGET)) {
                // when the target is non representable it is guaranteed to still exist and also
                // guaranteed to NOT appear in the scan, hence:
                //   1. APPLY the rename to make the object visible again
                //   2. remove the target from TDB to prevent it from being deleted
                op = APPLY;
                delBuffer.remove_(targetSOID);
            }
            renameConflictingLogicalObject_(_ds.getOA_(targetSOID), pc, fnt._fid, op, t);
        }

        switch (Operation.core(ops)) {
        case CREATE:
            return createLogicalObject_(pc, fnt._dir, og, t);
        case UPDATE:
            checkNotNull(sourceSOID);
            boolean moved = !_ds.resolve_(sourceSOID).equals(pc._path);
            SOID m = updateLogicalObject_(sourceSOID, pc, fnt._dir, t);
            // change of SOID indicate migration, in which case the tag file MUST NOT be recreated
            if (m.equals(sourceSOID)) scheduleTagFileFixIfNeeded(sourceSOID, pc);
            delBuffer.remove_(sourceSOID);
            return moved;
        case REPLACE:
            checkNotNull(targetSOID);
            replaceObject_(pc, fnt, delBuffer, sourceSOID, targetSOID, t);
            scheduleTagFileFixIfNeeded(targetSOID, pc);
            return true;
        default:
            throw new IllegalArgumentException("unhandled op:" + ops);
        }
    }


    public void scheduleTagFileFixIfNeeded(SOID soid, PathCombo pc)
    {
        if (!soid.oid().isAnchor()) return;
        SID sid = SID.anchorOID2storeSID(soid.oid());
        try {
            if (!_sfti.isSharedFolderRoot(sid, pc._absPath)) {
                // schedule fix instead of performing immediately
                // this is necessary to prevent interference with in-progress deletion operations
                // on some OSes (most notably interference was observed with syncdet tests on
                // Windows)
                scheduleTagFileFix(soid, pc);
            }
        } catch (Exception e) {
            l.error("failed to fix tag file for {} {} {}", soid, sid.toStringFormal(), pc, e);
        }
    }

    private void scheduleTagFileFix(final SOID soid, final PathCombo pc)
    {
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    fixTagFile(soid, pc);
                } catch (Exception e) {
                    l.error("failed to fix tag file for {} {} {}",
                            soid.sidx(), soid.oid().toStringFormal(), pc, e);
                }
            }
        }, TimeoutDeletionBuffer.TIMEOUT);
    }

    private void fixTagFile(SOID soid, PathCombo pc) throws Exception
    {
        SID sid = SID.anchorOID2storeSID(soid.oid());
        SIndex sidx = _sid2sidx.getNullable_(sid);
        // abort if store disappeared
        if (sidx == null) {
            l.warn("store disappeared before tag fix {}", sid, pc);
            return;
        }

        // Must resolve the anchor instead of the store root to work on flat linked storage
        Path p = _ds.resolveNullable_(soid);
        // abort if anchor moved
        if (p == null || !p.equals(pc._path)) {
            l.info("anchor moved before tag fix {} {}: {}", sid, pc, p);
            return;
        }

        _sfti.fixTagFileIfNeeded_(sid, pc._absPath);
    }

    /**
     * Assign the specified object a randomly generated FID which is not used by other objects
     *
     * NB: use a random UUID to avoid conflicts with real FIDs
     */
    private void assignRandomFID_(SOID soid, Trans t) throws SQLException
    {
        l.info("set random fid for {}", soid);
        _ds.randomizeFID_(soid, t);
    }

    /**
     * Rename the specified logical objects to an unused file name under the same parent as before.
     *
     * @param oa the OA of the logical object to be renamed
     * @param pc the path to the logical object, must be identical to what _ds.resolve_(oa) would
     * return
     */
    private void renameConflictingLogicalObject_(@Nonnull OA oa, PathCombo pc, FID fid,
            PhysicalOp op, Trans t)
            throws Exception
    {
        l.info("rename conflict {} {}", oa.soid(), pc);

        // can't rename the root
        checkState(!pc._path.isEmpty());
        checkState(pc._path.equals(_ds.resolve_(oa)),
                "pc._path " + pc._path + " does not match resolved oa " + _ds.resolve_(oa));

        // generate a new name for the logical object
        String name = oa.name();
        String pParent = _factFile.create(pc._absPath).getParent();
        while (true) {
            name = FileUtil.nextFileName(name);
            // avoid names that are taken by either logical or physical objects
            InjectableFile f = _factFile.create(pParent, name);
            if (f.exists()) continue;
            OID child = _ds.getChild_(oa.soid().sidx(), oa.parent(), name);
            if (child != null) continue;
            break;
        }

        l.info("move for confict {} {}->{}", oa.soid(), pc, name);

        // randomize FID in case of conflict
        FID fidTarget = oa.fid();
        if (fidTarget != null && fidTarget.equals(fid)) {
            assignRandomFID_(oa.soid(), t);
        }

        // rename the logical object
        _om.moveInSameStore_(oa.soid(), oa.parent(), name, op, true, t);
    }

    /**
     * @return whether a new logical object corresponding to the physical object is created
     */
    private boolean createLogicalObject_(PathCombo pc, boolean dir, OIDGenerator og, Trans t)
            throws Exception
    {
        SOID soidParent = _ds.resolveThrows_(pc._path.removeLast());
        OA oaParent = _ds.getOA_(soidParent);
        if (oaParent.isExpelled()) {
            // after the false return, scanner or linker should create the parent and recurse down
            // to the child again. exact implementations depend on operating systems.
            l.warn("parent is expelled. ignore child creation of " + pc);
            return false;
        }

        if (oaParent.isAnchor()) soidParent = _ds.followAnchorThrows_(oaParent);

        // create the object
        OID oid = dir ? _sfti.getOIDForAnchor_(soidParent.sidx(), pc, t) : null;
        Type type = oid != null ? ANCHOR : (dir ? DIR : FILE);
        if (oid == null) oid = og.generate_(dir, pc._path);
        SOID soid = _oc.createMetaForLinker_(type, oid, soidParent, pc._path.last(), t);
        l.info("created {} {}", soid, pc);
        if (!dir) {
            _ds.createCA_(soid, KIndex.MASTER, t);
            applyModification_(soid, _factFile.create(pc._absPath), t);
        }
        return true;
    }

    /**
     * Update a logical object to match a physical object
     * @param soid logical object to update
     * @param pc physical path from which to derive required updates, if any
     * @param dir whether the physical object is a directory
     * @return SOID after update (changes if migration occurs)
     *
     * This function may:
     *  * rename the logical object to match the physical path
     *  * update the master CA if the object is a file and is deemed to have changed
     */
    private SOID updateLogicalObject_(@Nonnull SOID soid, PathCombo pc, boolean dir, Trans t)
            throws Exception
    {
        // move the logical object if it's at a different path
        Path pLogical = _ds.resolve_(soid);
        Path pPhysical = pc._path;

        l.debug("update {} {}", pLogical, pPhysical);

        if (!pPhysical.equals(pLogical)) {
            // the two paths differ

            // identify name conflict
            SOID soidConflict = _ds.resolveNullable_(pPhysical);
            assert soidConflict == null
                    : soid + " " + pLogical + " " + soidConflict + " " + pPhysical;


            // move the logical object
            l.info("move {} {}->{}", soid, pLogical, pPhysical);
            Path pathToParent = pPhysical.removeLast();
            SOID soidToParent = _ds.resolveThrows_(pathToParent);
            OA oaToParent = _ds.getOA_(soidToParent);
            if (oaToParent.isAnchor()) soidToParent = _ds.followAnchorThrows_(oaToParent);
            soid = _imc.move_(soid, soidToParent, pPhysical.last(), MAP, t);
        }

        // update content if needed
        if (!dir) detectAndApplyModification_(soid, pc._absPath, false, t);

        return soid;
    }

    /**
     * Update the target object to reflect the current state of the physical object at the target
     * path. Any source object will be removed
     *
     * @param pc target path
     * @param fnt FID and type of the physical object at the target path
     * @param sourceSOID object that was previously mapped to the physical object (via FID), if any
     * @param targetSOID object that was previously mapped to the target path
     */
    private void replaceObject_(PathCombo pc, FIDAndType fnt, IDeletionBuffer delBuffer,
            @Nullable SOID sourceSOID, @Nonnull SOID targetSOID, Trans t) throws Exception
    {
        if (sourceSOID != null) {
            // When an existing object is moved over another existing (admitted) object we want
            // this to be treated as an update to the target object and a deletion of the source
            // object instead of causing a rename of the target and a move of the source. This
            // is important to prevent the creation of duplicate files when a user tries to seed
            // a shared folder *after* some metadata was retrieved but before content could be
            // downloaded. It also helps avoiding unnecessary transfers in such a scenario and
            // can reduce the amount of aliasing performed.
            l.info("move over {} {}", sourceSOID, targetSOID);
            cleanup_(sourceSOID, t);
        }

        // Link the physical object to the logical object by replacing the FID.
        l.info("replace {} {}", targetSOID, pc);

        // update the FID of that object
        _ds.setFID_(targetSOID, fnt._fid, t);

        // ideally we would always update content on FID change, unfortunately some filesystems have
        // ephemeral FIDs (FAT on Linux for instance) so we have to be careful to avoid generating
        // false updates on every reboot...
        boolean forceUpdate = sourceSOID != null;
        if (!fnt._dir) detectAndApplyModification_(targetSOID, pc._absPath, forceUpdate, t);

        delBuffer.remove_(targetSOID);
    }

    private void cleanup_(SOID sourceSOID, Trans t) throws SQLException
    {
        // We have to change the FID associated with the source object before we assign it to
        // the target.
        // Ideally we'd just reset the FID of the target but that would require cleaning up all
        // CAs to avoid violating OA consistency invariants. This is not acceptable because even
        // though the source object will most likely be deleted when it clears out the deletion
        // buffer, there is a slim but non-zero chance that new content is present for that
        // object so we cannot simply delete it immediately because that would create a new
        // OID and lose any existing conflict branch.
        // The solution is to assign a random FID
        assignRandomFID_(sourceSOID, t);
        // This approach has the problem that a sequence of overlapping moves (B->C; A->B) or a
        // swap (A<->B) may miss some content updates if the files involved have the same size
        // and timestamp. We work around that by assigning a negative size to the MASTER branch
        // of the source to make sure detectAndApplyModification_ will consider any content that
        // appear at the path of the source object to be a modification
        OA sourceOA = _ds.getOA_(sourceSOID);
        if (sourceOA.isFile() && sourceOA.caMasterNullable() != null) {
            _ds.setCA_(new SOKID(sourceSOID, KIndex.MASTER), -1L, 0L, null, t);
        }
    }

    /**
     * Bump local tick for a given file if the content has changed
     *
     * Change is determined by a heuristic based on length and mtime. Content hashing may also be
     * used to avoid false positives when only the timestamp has changed.
     *
     * @param force Always bump local tick, regardless of length and mtime
     */
    private void detectAndApplyModification_(SOID soid, String absPath, boolean force, Trans t)
            throws IOException, SQLException
    {
        OA oa = _ds.getOA_(soid);
        checkState(oa.isFile(), "%s", oa);
        CA ca = oa.caMasterNullable();
        InjectableFile f = _factFile.create(absPath);

        l.debug("detect {}, {} {}", absPath, ca, f);

        if (!(force || ca == null || f.wasModifiedSince(ca.mtime(), ca.length()))) {
            if (_ds.getCAHash_(new SOKID(soid, KIndex.MASTER)) != null) {
                l.debug("ignored {} {}", ca, f);
                return;
            }
        }
        if (ca == null) {
            _ds.createCA_(soid, KIndex.MASTER, t);
        }
        applyModification_(soid, f, t);
    }

    /**
     * @pre MASTER CA exists
     */
    private void applyModification_(SOID soid, InjectableFile f, Trans t)
            throws IOException, SQLException
    {
        final long length = f.length();
        final long mtime = f.lastModified();

        if (_hq.requestHash_(soid, f, length, mtime, t)) {
            l.info("hashing {} {} {}", soid, mtime, length);
        } else {
            l.debug("redundant hash req {} {} {}", soid, length, mtime);
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.first.OIDGenerator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.analytics.Analytics;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableDriver;
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
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.aerofs.lib.obfuscate.ObfuscatingFormatters.obfuscatePath;

/**
 * This class is in charge of executing whatever filesystem and db operations {@link MightCreate}
 * deems necessary to keep the mapping between logical and physical objects up-to-date
 */
class MightCreateOperations
{
    private static Logger l = Loggers.getLogger(MightCreateOperations.class);

    private final DirectoryService _ds;
    private final OIDGenerator _og;
    private final ObjectMover _om;
    private final ObjectCreator _oc;
    private final VersionUpdater _vu;
    private final InjectableFile.Factory _factFile;
    private final SharedFolderTagFileAndIcon _sfti;
    private final InjectableDriver _dr;
    private final Analytics _a;

    static enum Operation
    {
        // the following "core" operations are mutually exclusive
        Create,
        Update,
        Replace,

        // the following "flags" can be combined with some of the above ops
        RenameTarget,
        RandomizeSourceFID;

        static private final EnumSet<Operation> PRE = EnumSet.of(RenameTarget, RandomizeSourceFID);
        static private final EnumSet<Operation> CORE = EnumSet.complementOf(PRE);

        // extract core operation (assume exactly one core operation is present)
        static private Operation core(Set<Operation> ops)
        {
            return Iterables.getOnlyElement(Sets.intersection(ops, Operation.CORE));
        }
    }

    @Inject
    public MightCreateOperations(DirectoryService ds, ObjectMover om, ObjectCreator oc,
            InjectableDriver driver, VersionUpdater vu, InjectableFile.Factory factFile,
            SharedFolderTagFileAndIcon sfti, Analytics a, OIDGenerator og)
    {
        _ds = ds;
        _og = og;
        _oc = oc;
        _om = om;
        _vu = vu;
        _factFile = factFile;
        _sfti = sfti;
        _dr = driver;
        _a = a;
    }

    /**
     * @return whether an object was created or replaced
     */
    public boolean executeOperation_(Set<Operation> ops, SOID sourceSOID, SOID targetSOID,
            PathCombo pc, FIDAndType fnt, IDeletionBuffer delBuffer, Trans t) throws Exception
    {
        if (ops.contains(Operation.RandomizeSourceFID)) assignRandomFID_(sourceSOID, t);

        if (ops.contains(Operation.RenameTarget)) {
            renameConflictingLogicalObject_(_ds.getOA_(targetSOID), pc, fnt._fid, t);
        }

        switch (Operation.core(ops)) {
        case Create:
            createLogicalObject_(pc, fnt._dir, t);
            return true;
        case Update:
            assert sourceSOID != null;
            updateLogicalObject_(sourceSOID, pc, fnt._dir, t);
            delBuffer.remove_(sourceSOID);
            return false;
        case Replace:
            assert targetSOID != null;
            replaceObject_(pc, fnt, delBuffer, sourceSOID, targetSOID, t);
            return true;
        default:
            throw SystemUtil.fatalWithReturn("unhandled op:" + ops);
        }
    }

    /**
     * Assign the specified object a randomly generated FID which is not used by other objects
     */
    private void assignRandomFID_(SOID soid, Trans t) throws SQLException
    {
        l.info("set random fid for {}", soid);
        byte[] bs = new byte[_dr.getFIDLength()];
        while (true) {
            Util.rand().nextBytes(bs);
            FID fid = new FID(bs);
            if (_ds.getSOIDNullable_(fid) == null) {
                _ds.setFID_(soid, fid, t);
                return;
            }
        }
    }

    /**
     * Rename the specified logical objects to an unused file name under the same parent as before.
     *
     * @param oa the OA of the logical object to be renamed
     * @param pc the path to the logical object, must be identical to what _ds.resolve_(oa) would
     * return
     */
    private void renameConflictingLogicalObject_(@Nonnull OA oa, PathCombo pc, FID fid, Trans t)
            throws Exception
    {
        l.info("rename conflict {} {}", oa.soid(), pc);

        // can't rename the root
        assert pc._path.elements().length > 0;
        assert pc._path.equalsIgnoreCase(_ds.resolve_(oa)) :
                "pc._path " + pc._path + " does not match resolved oa " + _ds.resolve_(oa);

        // generate a new name for the logical object
        String name = oa.name();
        String pParent = _factFile.create(pc._absPath).getParent();
        while (true) {
            name = Util.nextFileName(name);
            // avoid names that are taken by either logical or physical objects
            InjectableFile f = _factFile.create(pParent, name);
            if (f.exists()) continue;
            OID child = _ds.getChild_(oa.soid().sidx(), oa.parent(), name);
            if (child != null) continue;
            break;
        }

        l.info("move for confict {} {}->{}", oa.soid(), pc, obfuscatePath(name));

        // randomize FID in case of conflict
        FID fidTarget = oa.fid();
        if (fidTarget != null && fidTarget.equals(fid)) {
            assignRandomFID_(oa.soid(), t);
        }

        // rename the logical object
        _om.moveInSameStore_(oa.soid(), oa.parent(), name, MAP, false, true, t);
    }

    /**
     * @return whether a new logical object corresponding to the physical object is created
     */
    private boolean createLogicalObject_(PathCombo pc, boolean dir, Trans t)
            throws ExNotFound, SQLException, ExExpelled, IOException, ExAlreadyExist, ExNotDir,
            ExStreamInvalid
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
        OID oid = dir ? _sfti.getOIDForAnchor_(pc, t) : null;
        Type type = oid != null ? ANCHOR : (dir ? DIR : FILE);
        if (oid == null) oid = _og.generate_(dir, pc._path);
        _oc.create_(type, oid, soidParent, pc._path.last(), MAP, t);
        _a.incSaveCount();
        l.info("created {} {}", new SOID(soidParent.sidx(), oid), pc);
        return true;
    }

    /**
     * Update a logical object to match a physical object
     * @param soid logical object to update
     * @param pc physical path from which to derive required updates, if any
     * @param dir whether the physical object is a directory
     *
     * This function may:
     *  * rename the logical object to match the physical path
     *  * update the master CA if the object is a file and is deemed to have changed
     */
    private void updateLogicalObject_(@Nonnull SOID soid, PathCombo pc, boolean dir, Trans t)
            throws Exception
    {
        // move the logical object if it's at a different path
        Path pLogical = _ds.resolve_(soid);
        Path pPhysical = pc._path;

        if (!pPhysical.equals(pLogical)) {
            // the two paths differ

            // identify name conflict
            SOID soidConflict = _ds.resolveNullable_(pPhysical);
            assert soidConflict == null ||
                    (soid.equals(soidConflict) && pPhysical.equalsIgnoreCase(pLogical))
                    : soid + " " + pLogical + " " + soidConflict + " " + pPhysical;


            // move the logical object
            l.info("move {} {}->{}", soid, obfuscatePath(pLogical), obfuscatePath(pPhysical));
            Path pathToParent = pPhysical.removeLast();
            SOID soidToParent = _ds.resolveThrows_(pathToParent);
            OA oaToParent = _ds.getOA_(soidToParent);
            if (oaToParent.isAnchor()) soidToParent = _ds.followAnchorThrows_(oaToParent);
            soid = _om.move_(soid, soidToParent, pPhysical.last(), MAP, t);
        }

        // update content if needed
        if (!dir) detectAndApplyModification_(soid, pc._absPath, false, t);
    }

    /**
     * Update the target object to reflect the current state of the physical object at the target
     * path. Any source object will be removed
     *
     * @param pc target path
     * @param fnt FID and type of the physical object at the target path
     * @param sourceSOID object that was previously mapped to the physical object (via FID), if any
     * @param targetSOID object that was previosuly mapped to the target path
     */
    private void replaceObject_(PathCombo pc, FIDAndType fnt, IDeletionBuffer delBuffer,
            @Nullable SOID sourceSOID, @Nonnull SOID targetSOID, Trans t) throws Exception
    {
        if (sourceSOID != null) cleanup_(sourceSOID, targetSOID, t);

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

    private void cleanup_(SOID sourceSOID, SOID targetSOID, Trans t) throws SQLException
    {
        // When an existing object is moved over another existing (admitted) object we want
        // this to be treated as an update to the target object and a deletion of the source
        // object instead of causing a rename of the target and a move of the source. This
        // is important to prevent the creation of duplicate files when a user tries to seed
        // a shared folder *after* some metadata was retrieved but before content could be
        // downloaded. It also helps avoiding unnecessary transfers in such a scenario and
        // can reduce the amount of aliasing performed.
        l.info("move over {} {}", sourceSOID, targetSOID);

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

    private void detectAndApplyModification_(SOID soid, String absPath, boolean force, Trans t)
            throws IOException, SQLException
    {
        OA oa = _ds.getOA_(soid);
        assert oa.isFile() : oa;
        CA ca = oa.caMasterNullable();
        InjectableFile f = _factFile.create(absPath);

        if (!(force || ca == null || f.wasModifiedSince(ca.mtime(), ca.length()))) {
            l.debug("ignored {} {}", ca, f);
            return;
        }

        if (ca == null) {
            // The master CA is absent. This may happen when a file's metadata has been downloaded
            // but the content hasn't been so. Create the master CA in this case.
            l.warn("create master CA for {}", soid);
            _ds.createCA_(soid, KIndex.MASTER, t);
        }

        l.info("modify {}", soid);
        _vu.update_(new SOCKID(soid, CID.CONTENT), t);
        _a.incSaveCount();
    }
}

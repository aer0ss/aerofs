package com.aerofs.daemon.core.linker;

import static com.aerofs.daemon.core.ds.OA.Type.*;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;

import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.lib.obfuscate.ObfuscatingFormatters.*;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.first.OIDGenerator;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Param;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.id.SOKID;
import org.slf4j.Logger;

import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.fs.HdMoveObject;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.analytics.Analytics;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MightCreate
{
    private static Logger l = Loggers.getLogger(MightCreate.class);

    private final IgnoreList _il;
    private final DirectoryService _ds;
    private final HdMoveObject _hdmo;
    private final ObjectMover _om;
    private final ObjectCreator _oc;
    private final VersionUpdater _vu;
    private final InjectableFile.Factory _factFile;
    private final InjectableDriver _dr;
    private final Analytics _a;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;
    private final SharedFolderTagFileAndIcon _sfti;
    private final OIDGenerator _og;

    @Inject
    public MightCreate(IgnoreList ignoreList, DirectoryService ds, HdMoveObject hdmo,
            ObjectMover om, ObjectCreator oc, InjectableDriver driver,
            VersionUpdater vu, InjectableFile.Factory factFile, SharedFolderTagFileAndIcon sfti,
            Analytics a, CfgAbsRootAnchor cfgAbsRootAnchor, OIDGenerator og)
    {
        _il = ignoreList;
        _ds = ds;
        _hdmo = hdmo;
        _oc = oc;
        _om = om;
        _vu = vu;
        _factFile = factFile;
        _dr = driver;
        _a = a;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
        _sfti = sfti;
        _og = og;
    }

    public static enum Result {
        NEW_OR_REPLACED_FOLDER, // the physical object is a folder and a new,
                                // corresponding logical object has been created
        EXISTING_FOLDER,        // the physical object is a folder and the corresponding
                                // logical object already exists before the method call
        FILE,                   // the physical object is a file
        IGNORED,                // the physical object is ignored
    }

    private static enum Condition {
        SAME_FID_SAME_TYPE,             // found a logical object with same FID and same type
        SAME_PATH_SAME_TYPE_ADMITTED,   // found an admitted logical object with different FID, same
                                        // path, and same type
        SAME_PATH_DIFF_TYPE_OR_EXPELLED,// found a logical object with different FID and same path,
                                        // but it either has different type or is expelled
        NO_MATCHING,                    // no matching logical object is found
    }

    /**
     * To handle the cases of SAME_PATH_SAME_TYPE_ADMITTED and SAME_PATH_DIFF_TYPE_OR_EXPELLED
     * the logical OA for the physical path is required, so we embed that in this wrapper class.
     * TODO (MJ) this class smells. The best solution to remove it is to replace the switch
     * statement in mightCreate with polymorphism, and embed the oaSamePath in the polymorphic
     * subclasses.
     */
    private class ConditionAndOA
    {
        public final Condition _cond;
        @Nullable public final OA _oaSamePath;

        ConditionAndOA(Condition cond)
        {
            _cond = cond;
            _oaSamePath = null;
        }

        ConditionAndOA(Condition cond, @Nonnull OA oaSamePath)
        {
            switch(cond) {
            case SAME_PATH_DIFF_TYPE_OR_EXPELLED:
            case SAME_PATH_SAME_TYPE_ADMITTED:
                break;
            default:
                assert false;
            }
            _cond = cond;
            _oaSamePath = oaSamePath;
        }
    }

    /**
     * N.B. This method must throw on _any_ error instead of ignoring them and proceeding. See
     * Comment (A) in ScanSession.
     *
     * @param pcPhysical the path of the physical file
     * @throws ExNotFound if either the physical object or the parent of the logical object is not
     * found when creating or moving the object.
     */
    public Result mightCreate_(PathCombo pcPhysical, IDeletionBuffer delBuffer, Trans t)
            throws Exception
    {
        if (deleteIfInvalidTagFile(pcPhysical._path) || _il.isIgnored_(pcPhysical._path.last())) {
            return Result.IGNORED;
        }

        FIDAndType fnt = _dr.getFIDAndType(pcPhysical._absPath);

        // OS-specific files should be ignored
        if (fnt == null) return Result.IGNORED;

        if (l.isDebugEnabled()) l.debug(pcPhysical + ":" + fnt._fid);

        // TODO acl checking

        SOID parent = _ds.resolveNullable_(pcPhysical._path.removeLast());
        OA oaParent = parent == null ? null : _ds.getOANullable_(parent);
        if (oaParent == null || oaParent.isExpelled()) {
            // if we get a notification about a path for which the parent is expelled or not
            // present, we are most likely hitting a race between expulsion and creation so we
            // should ignore the notification and wait for the one about the new parent, which
            // will in turn trigger a scan
            l.warn("expel/create race {}", obfuscatePath(pcPhysical._path));
            return Result.IGNORED;
        }

        ////////
        // Determine condition. See enum Condition

        SOID soid = _ds.getSOIDNullable_(fnt._fid);
        ConditionAndOA condAndOA;
        try {
            condAndOA = determineCondition_(soid, fnt, pcPhysical._path, delBuffer, t);
        } catch (ExHardLinkDetected e) {
            // Hard-link handling:
            // Generally, we only let one of the hard-linked physical files/folders to
            // be recorded as a logical object in the database; the rest of the physical objects
            // are subsequently skipped by the scan_() method, which results in the removal of
            // the object from the MetaDatabase.
            //
            // Specifically, in lieu of a hardlink, we ignore the current physical object and leave
            // the logical object intact in the database.
            // NOTE: If the user deletes the object that we decided to keep in our database
            //       after a full scan happened, the other devices will see one of the other
            //       hard linked object appear only after another full scan.
            return Result.IGNORED;
        }

        ////////
        // Perform operations suited to each condition

        boolean createdOrReplaced;
        switch (condAndOA._cond) {
        case SAME_FID_SAME_TYPE:
            // Update the logical object to reflect changes on the physical one, if any
            updateLogicalObject_(soid, pcPhysical, fnt._dir, t);
            delBuffer.remove_(soid);
            createdOrReplaced = false;
            break;
        case SAME_PATH_SAME_TYPE_ADMITTED:
            // Link the physical object to the logical object by replacing the FID.
            replaceObject_(pcPhysical, fnt, delBuffer, soid, condAndOA._oaSamePath.soid(), t);
            createdOrReplaced = true;
            break;
        case SAME_PATH_DIFF_TYPE_OR_EXPELLED:
            // The logical object has a different type or is expelled and thus can't link to the
            // physical object by replacing the FID.

            // First, move the existing logical object out of way.
            renameConflictingLogicalObject_(condAndOA._oaSamePath, pcPhysical, t);

            // Second, treat it as if no corresponding logical object is found, so that the system
            // will create a new logical object for the physical object.
            createdOrReplaced = createLogicalObject_(pcPhysical, fnt._dir, t);

            // do not remove the existing logical object from the deletion buffer, so it will be
            // deleted if it's not moved to other locations later on.
            break;
        default:
            assert condAndOA._cond == Condition.NO_MATCHING;
            createdOrReplaced = createLogicalObject_(pcPhysical, fnt._dir, t);
            break;
        }

        if (fnt._dir) {
            return createdOrReplaced ? Result.NEW_OR_REPLACED_FOLDER : Result.EXISTING_FOLDER;
        } else {
            return Result.FILE;
        }
    }

    private boolean deleteIfInvalidTagFile(Path path) throws IOException, SQLException
    {
        if (!path.last().equals(Param.SHARED_FOLDER_TAG)) return false;

        // Remove any invalid tag file (i.e tag file under non-anchor)
        SOID parent = _ds.resolveNullable_(path.removeLast());
        if (parent != null) {
            OA oa = _ds.getOA_(parent);
            if (!oa.isAnchor()) _sfti.deleteTagFileAndIconIn(path.removeLast());
        }
        return true;
    }


    /**
     * @pre fnt is the FID from the file system for physicalPath, and soid is the logical object
     * recorded for that FID.
     */
    private ConditionAndOA determineCondition_(@Nullable SOID soid, FIDAndType fnt,
            Path physicalPath, IDeletionBuffer delBuffer, Trans t)
            throws Exception
    {
        SOID soidSamePath = _ds.resolveNullable_(physicalPath);
        if (soid == null) {
            // Logical object of the same FID is not found.
        } else {
            final OA oa = _ds.getOA_(soid);
            assert !oa.isExpelled() : oa;

            Path logicalPath = _ds.resolveNullable_(soid);
            if (logicalPath != null) throwIfHardLinkDetected_(logicalPath, physicalPath, fnt._fid);

            if (fnt._dir == _ds.getOA_(soid).isDirOrAnchor()) {
                // When an existing object is moved over another existing (admitted) object we want
                // this to be treated as an update to the target object and a deletion of the source
                // object instead of causing a rename of the target and a move of the source. This
                // is important to prevent the creation of duplicate files when a user tries to seed
                // a shared folder *after* some metadata was retrieved but before content could be
                // downloaded. It also helps avoiding unnecessary transfers in such a scenario and
                // can reduce the amount of aliasing performed.
                // NB: we detect collisions at OID-level instead of path-level because the logical
                // pathes are case-insensitive
                if (soidSamePath != null && !soidSamePath.equals(soid)) {
                    ConditionAndOA cond = determineLogicalCondition_(fnt, soidSamePath, delBuffer);
                    if (cond._cond == Condition.SAME_PATH_SAME_TYPE_ADMITTED) return cond;
                }

                return new ConditionAndOA(Condition.SAME_FID_SAME_TYPE);
            } else {
                // The logical object has the same FID but different type than the physical object.
                // This may happen if 1) the OS deletes an object and soon reuses the same FID to
                // create a new object of a different type (this has been observed on a Ubuntu test
                // VM). This can also happen 2) on filesystems with ephemeral FIDs such as FAT on
                // Linux. In either case, we assign the logical object with a random FID and proceed
                // to the condition determination code.
                l.info("set random fid for " + soid);
                assignRandomFID_(soid, t);
            }
        }

        return determineLogicalCondition_(fnt, soidSamePath, delBuffer);
    }

    private ConditionAndOA determineLogicalCondition_(FIDAndType fnt, SOID soidSamePath,
            IDeletionBuffer delBuffer) throws SQLException
    {
        if (soidSamePath == null) {
            return new ConditionAndOA(Condition.NO_MATCHING);
        } else {
            OA oaSamePath;
            // Found a logical object with a different FID but the same path.
            oaSamePath = _ds.getOA_(soidSamePath);

            // The assertion below is guaranteed by the above code. N.B. oa.fid() may be null if
            // the no branch is present. See also detectAndApplyModification_()
            assert !fnt._fid.equals(oaSamePath.fid());
            // This assertion would fail if a new type other than file/dir/anchor is created in
            // the future. The assertion is needed for the "fnt._dir == ...isDirOrAnchor()"
            // check below.
            assert oaSamePath.isDirOrAnchor() != oaSamePath.isFile();

            if (oaSamePath.isExpelled()) {
                // MightDelete.shouldNotDelete should prevent expelled objects from entering the
                // deletion buffer.
                assert !delBuffer.contains_(soidSamePath);
                return new ConditionAndOA(Condition.SAME_PATH_DIFF_TYPE_OR_EXPELLED, oaSamePath);
            } else if (fnt._dir == oaSamePath.isDirOrAnchor()) {
                // The logical object is admitted and has the same type as the physical object.
                return new ConditionAndOA(Condition.SAME_PATH_SAME_TYPE_ADMITTED, oaSamePath);
            } else {
                // the logical object has a different type
                return new ConditionAndOA(Condition.SAME_PATH_DIFF_TYPE_OR_EXPELLED, oaSamePath);
            }
        }
    }

    private class ExHardLinkDetected extends Exception
    {
        private static final long serialVersionUID = 0L;
    }

    /**
     * Determine whether a hardlink exists between logicalPath and physicalPath.
     * @param logicalPath path in the DB when looking up {@code physicalFID}
     * @param physicalPath path that currently represents {@code physicalFID} on the FS
     * @throws ExHardLinkDetected when a hardlink has been detected between logicalPath
     * and physicalPath
     */
    private void throwIfHardLinkDetected_(Path logicalPath, Path physicalPath, FID physicalFID)
            throws SQLException, ExHardLinkDetected
    {
        // If the logical and physical paths are not equal and the
        // physical file with that logical path has the same FID as the current file
        // then we detected a hard link between the logical object and the current
        // physical object.

        if (!physicalPath.equalsIgnoreCase(logicalPath)) {
            // Do a case insensitive equals to tolerate both case preserving and insensitive
            // file systems to make sure we don't ignore mv operations on files that have
            // the same letters in their name.
            // Example scenario is: mv ./hello.txt ./HELLO.txt

            try {
                String absLogicalPath = logicalPath.toAbsoluteString(_cfgAbsRootAnchor.get());
                FID logicalFID = _dr.getFID(absLogicalPath);

                if ((logicalFID != null) && (logicalFID.equals(physicalFID))) {
                    throw new ExHardLinkDetected();
                }
            } catch (IOException e) {
                // File is not found or there was an IO exception, in either case
                // this means that the FID of the logical object was not found, most likely
                // deleted, so proceed to update the DB.
            }
        }
    }

    /**
     * Assign the specified object a randomly generated FID which is not used by other objects
     */
    private void assignRandomFID_(SOID soid, Trans t) throws SQLException
    {
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

    private void updateLogicalObject_(SOID soid, PathCombo pcPhysical, boolean dir, Trans t)
            throws Exception
    {
        // move the logical object if it's at a different path
        Path pLogical = _ds.resolve_(soid);
        Path pPhysical = pcPhysical._path;

        if (!pPhysical.equals(pLogical)) {
            // the two paths differ

            // identify name conflict
            SOID soidConflict = _ds.resolveNullable_(pPhysical);
            if (soidConflict != null && !soid.equals(soidConflict)) {
                // the path is taken by another logical object. rename it
                OA oaConflict = _ds.getOA_(soidConflict);
                renameConflictingLogicalObject_(oaConflict, pcPhysical, t);
            } else if (soidConflict != null) {
                // the soids are equal: the DirectoryService should only return an equal soid if
                // the two paths are equal when ignoring case
                assert pPhysical.equalsIgnoreCase(pLogical) : pPhysical + " " + pLogical;
            }

            // move the logical object
            l.info("move " + soid + ":" + obfuscatePath(pLogical) + "->" + obfuscatePath(pPhysical));
            Path pathToParent = pPhysical.removeLast();
            SOID soidToParent = _ds.resolveThrows_(pathToParent);
            OA oaToParent = _ds.getOA_(soidToParent);
            if (oaToParent.isAnchor()) soidToParent = _ds.followAnchorThrows_(oaToParent);
            soid = _hdmo.move_(soid, soidToParent, pPhysical.last(), MAP, t);
        }

        // update content if needed
        if (!dir) detectAndApplyModification_(soid, pcPhysical._absPath, false, t);
    }

    /**
     * Rename the specified logical objects to an unused file name under the same parent as before.
     *
     * @param oa the OA of the logical object to be renamed
     * @param pc the path to the logical object, must be identical to what _ds.resolve_(oa) would
     * return
     */
    private void renameConflictingLogicalObject_(@Nonnull OA oa, PathCombo pc, Trans t)
            throws Exception
    {
        if (l.isInfoEnabled()) {
            l.info("rename conflict " + oa.soid() + ":" + pc);
        }

        // can't rename the root
        assert pc._path.elements().length > 0;
        assert pc._path.equalsIgnoreCase(_ds.resolve_(oa)) :
                "pc._path " + pc._path + " does not match resolved oa " + _ds.resolve_(oa);

        // generate a new name for the logical object
        String name = oa.name();
        while (true) {
            name = Util.nextFileName(name);
            // avoid names that are taken by either logical or physical objects
            String pParent = _factFile.create(pc._absPath).getParent();
            InjectableFile f = _factFile.create(pParent, name);
            if (f.exists()) continue;
            OID child = _ds.getChild_(oa.soid().sidx(), oa.parent(), name);
            if (child != null) continue;
            break;
        }

        if (l.isInfoEnabled()) {
            l.info("move for confict " + oa.soid() + ":" + pc + "->" + obfuscatePath(name));
        }

        // rename the logical object
        _om.moveInSameStore_(oa.soid(), oa.parent(), name, MAP, false, true, t);
    }

    /**
     * @return whether a new logical object corresponding to the physical object is created
     */
    private boolean createLogicalObject_(PathCombo pcPhysical, boolean dir, Trans t)
            throws ExNotFound, SQLException, ExExpelled, IOException, ExAlreadyExist, ExNotDir,
            ExStreamInvalid
    {
        // create the object
        SOID soidParent = _ds.resolveThrows_(pcPhysical._path.removeLast());
        OA oaParent = _ds.getOA_(soidParent);
        if (oaParent.isExpelled()) {
            // after the false return, scanner or linker should create the parent and recurse down
            // to the child again. exact implementations depend on operating systems.
            l.warn("parent is expelled. ignore child creation of " + pcPhysical);
            return false;
        } else {
            if (oaParent.isAnchor()) soidParent = _ds.followAnchorThrows_(oaParent);
            OID oid = dir ? _sfti.getOIDForAnchor_(pcPhysical, t) : null;
            Type type = oid != null ? ANCHOR : (dir ? DIR : FILE);
            if (oid == null) oid = _og.generate_(dir, pcPhysical._path);
            _oc.create_(type, oid, soidParent, pcPhysical._path.last(), MAP, t);
            _a.incSaveCount();
            l.info("created " + new SOID(soidParent.sidx(), oid) + " " + pcPhysical);
            return true;
        }
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
        l.info("replace " + targetSOID + ":" + pc);

        // update the FID of that object
        _ds.setFID_(targetSOID, fnt._fid, t);

        // ideally we would always update content on FID change, unfortunately some filesystems have
        // ephemeral FIDs (FAT on Linux for instance) so we have to be careful to avoid generating
        // false updates on every reboot...
        boolean forceUpdate = sourceSOID != null;
        if (!fnt._dir) detectAndApplyModification_(targetSOID, pc._absPath, forceUpdate, t);

        delBuffer.remove_(targetSOID);
    }

    void cleanup_(SOID sourceSOID, SOID targetSOID, Trans t) throws SQLException
    {
        // When an existing object is moved over another existing (admitted) object we want
        // this to be treated as an update to the target object and a deletion of the source
        // object instead of causing a rename of the target and a move of the source. This
        // is important to prevent the creation of duplicate files when a user tries to seed
        // a shared folder *after* some metadata was retrieved but before content could be
        // downloaded. It also helps avoiding unnecessary transfers in such a scenario and
        // can reduce the amount of aliasing performed.
        l.info("move over " + sourceSOID + " " + targetSOID);

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
        if (_ds.getOA_(sourceSOID).isFile()) {
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
            l.info("ignored " + ca.mtime() + " " + ca.length() + " " + f.getLengthOrZeroIfNotFile());
            return;
        }

        if (ca == null) {
            // The master CA is absent. This may happen when a file's metadata has been downloaded
            // but the content hasn't been so. Create the master CA in this case.
            l.warn("absent master CA on " + soid + ". create it");
            _ds.createCA_(soid, KIndex.MASTER, t);
        }

        l.info("modify " + soid);
        _vu.update_(new SOCKID(soid, CID.CONTENT), t);
        _a.incSaveCount();
    }
}

package com.aerofs.daemon.core.linker;

import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.fs.HdCreateObject;
import com.aerofs.daemon.core.fs.HdMoveObject;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.analytics.Analytics;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

public class MightCreate
{
    private static Logger l = Util.l(MightCreate.class);

    private final IgnoreList _il;
    private final DirectoryService _ds;
    private final HdMoveObject _hdmo;
    private final ObjectMover _om;
    private final ObjectCreator _oc;
    private final ComMonitor _cm;
    private final InjectableFile.Factory _factFile;
    private final InjectableDriver _dr;
    private final Analytics _a;
    @Inject
    public MightCreate(IgnoreList ignoreList, DirectoryService ds, HdMoveObject hdmo,
            ObjectMover om, ObjectCreator oc, InjectableDriver driver, HdCreateObject hdco,
            ComMonitor cm, InjectableFile.Factory factFile, Analytics a)
    {
        _il = ignoreList;
        _ds = ds;
        _hdmo = hdmo;
        _oc = oc;
        _om = om;
        _cm = cm;
        _factFile = factFile;
        _dr = driver;
        _a = a;
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
     * N.B. This method must throw on _any_ error instead of ignoring them and proceeding. See
     * Comment (A) in HdScan.
     *
     * @param pcPhysical the path of the physical file
     * @throws com.aerofs.lib.ex.ExNotFound if the logical parent of an object
     * is not found when creating or moving the object.
     */
    public Result mightCreate_(PathCombo pcPhysical, IDeletionBuffer delBuffer, Trans t)
            throws Exception
    {
        if (_il.isIgnored_(pcPhysical._path.last())) return Result.IGNORED;

        FIDAndType fnt = _dr.getFIDAndType(pcPhysical._absPath);

        // OS-specific files should be ignored
        if (fnt == null) return Result.IGNORED;

        // TODO acl checking

        ////////
        // Determine condition. See enum Condition

        Condition cond;
        OA oaSamePath;
        SOID soid;

        soid = _ds.getSOID_(fnt._fid);
        if (soid == null) {
            // Logical object of the same FID is not found.
            cond = null;
        } else {
            assert !_ds.getOANullable_(soid).isExpelled();
            if (fnt._dir == _ds.getOANullable_(soid).isDirOrAnchor()) {
                cond = Condition.SAME_FID_SAME_TYPE;
            } else {
                // The logical object has the same FID but different type than the physical object.
                // This may happen if 1) the OS deletes an object and soon reuses the same FID to
                // create a new object of a different type (this has been observed on a Ubuntu test
                // VM). This can also happen 2) on filesystems with ephemeral FIDs such as FAT on
                // Linux. In either case, we assign the logical object with a random FID and proceed
                // to the condition determination code.
                l.info("set random fid for " + soid);
                assignRandomFID_(soid, t);
                cond = null;
            }
        }

        if (cond != null) {
            oaSamePath = null;
        } else {
            SOID soidSamePath = _ds.resolveNullable_(pcPhysical._path);
            if (soidSamePath == null) {
                oaSamePath = null;
                cond = Condition.NO_MATCHING;
            } else {
                // Found a logical object with a different FID but the same path.
                oaSamePath = _ds.getOANullable_(soidSamePath);

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
                    cond = Condition.SAME_PATH_DIFF_TYPE_OR_EXPELLED;
                } else if (fnt._dir == oaSamePath.isDirOrAnchor()) {
                    // The logical object is admitted and has the same type as the physical object.
                    cond = Condition.SAME_PATH_SAME_TYPE_ADMITTED;
                } else {
                    // the logical object has a different type
                    cond = Condition.SAME_PATH_DIFF_TYPE_OR_EXPELLED;
                }
            }
        }

        ////////
        // Perform operations suited to each condition

        boolean createdOrReplaced;
        switch (cond) {
        case SAME_FID_SAME_TYPE:
            // Update the logical object to reflect changes on the physical one, if any
            updateLogicalObject_(soid, pcPhysical, fnt._dir, t);
            delBuffer.remove_(soid);
            createdOrReplaced = false;
            break;
        case SAME_PATH_SAME_TYPE_ADMITTED:
            // Link the physical object to the logical object by replacing the FID.
            replaceFID_(oaSamePath.soid(), pcPhysical, fnt._dir, fnt._fid, t);
            delBuffer.remove_(oaSamePath.soid());
            createdOrReplaced = true;
            break;
        case SAME_PATH_DIFF_TYPE_OR_EXPELLED:
            // The logical object has a different type or is expelled and thus can't link to the
            // physical object by replacing the FID.

            // First, move the existing logical object out of way.
            renameConflictingLogicalObject_(oaSamePath, pcPhysical, t);

            // Second, treat it as if no corresponding logical object is found, so that the system
            // will create a new logical object for the physical object.
            createdOrReplaced = createLogicalObject_(pcPhysical, fnt._dir, t);

            // do not remove the existing logical object from the deletion buffer, so it will be
            // deleted if it's not moved to other locations later on.
            break;
        default:
            assert cond == Condition.NO_MATCHING;
            createdOrReplaced = createLogicalObject_(pcPhysical, fnt._dir, t);
            break;
        }

        if (fnt._dir) {
            return createdOrReplaced ? Result.NEW_OR_REPLACED_FOLDER : Result.EXISTING_FOLDER;
        } else {
            return Result.FILE;
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
            if (_ds.getSOID_(fid) == null) {
                _ds.setFID_(soid, fid, t);
                return;
            }
        }
    }

    private void updateLogicalObject_(SOID soid, PathCombo pcPhysical, boolean dir, Trans t)
            throws Exception
    {
        // move the logical object if it's at a different path
        Path pLogical = _ds.resolveNullable_(soid);
        assert pLogical != null;

        Path pPhysical = pcPhysical._path;
        if (!pPhysical.equals(pLogical)) {
            // the two paths differ

            // identify name conflict
            SOID soidConflict = _ds.resolveNullable_(pPhysical);
            if (soidConflict != null && !soid.equals(soidConflict)) {
                // the path is taken by another logical object. rename it
                OA oaConflict = _ds.getOANullable_(soidConflict);
                renameConflictingLogicalObject_(oaConflict, pcPhysical, t);
            } else if (soidConflict != null) {
                // the soids are equal: the DirectoryService should only return an equal soid if
                // the two paths are equal when ignoring case
                assert pPhysical.equalsIgnoreCase(pLogical);
            }

            // move the logical object
            l.info("move " + soid + ":" + PathCombo.toLogString(pLogical) + "->" +
                    PathCombo.toLogString(pPhysical));
            Path pathToParent = pPhysical.removeLast();
            SOID soidToParent = _ds.resolveThrows_(pathToParent);
            OA oaToParent = _ds.getOANullable_(soidToParent);
            if (oaToParent.isAnchor()) soidToParent = _ds.followAnchorThrows_(oaToParent);
            soid = _hdmo.move_(soid, soidToParent, pPhysical.last(), MAP, t);
        }

        // update content if needed
        if (!dir) detectAndApplyModification_(soid, pcPhysical._absPath, t);
    }

    private void replaceFID_(SOID soid, PathCombo pc, boolean dir, FID fid, Trans t)
            throws Exception
    {
        l.info("replace " + soid + ":" + PathCombo.toLogString(pc._path));

        // update the FID of that object
        _ds.setFID_(soid, fid, t);

        // update content if needed
        if (!dir) detectAndApplyModification_(soid, pc._absPath, t);
    }

    /**
     * Rename the specified logical objects to an unused file name under the same parent as before.
     *
     * @param oa the OA of the logical object to be renamed
     * @param pc the path to the logical object, must be identical to what _ds.resolve_(oa) would
     * return
     */
    private void renameConflictingLogicalObject_(OA oa, PathCombo pc, Trans t) throws Exception
    {
        if (l.isInfoEnabled()) {
            l.info("rename conflict " + oa.soid() + ":" + PathCombo.toLogString(pc._path));
        }

        // can't rename the root
        assert pc._path.elements().length > 0;
        assert pc._path.equalsIgnoreCase(_ds.resolve_(oa)) :
                "pc._path " + pc._path + " does not match resolved oa " + _ds.resolve_(oa);

        // generate a new name for the logical object
        String name = oa.name();
        while (true) {
            name = Util.newNextFileName(name);
            // avoid names that are taken by either logical or physical objects
            String pParent = _factFile.create(pc._absPath).getParent();
            InjectableFile f = _factFile.create(pParent, name);
            if (f.exists()) continue;
            OID child = _ds.getChild_(oa.soid().sidx(), oa.parent(), name);
            if (child != null) continue;
            break;
        }

        if (l.isInfoEnabled()) {
            l.info("move for confict " + oa.soid() + ":" + PathCombo.toLogString(pc._path) + "->" +
                    PathCombo.toLogString(name));
        }

        // rename the logical object
        _om.moveInSameStore_(oa.soid(), oa.parent(), name, MAP, false, true, t);
    }

    /**
     * @return whether a new logical object corresponding to the physical object is created
     */
    private boolean createLogicalObject_(PathCombo pcPhysical, boolean dir, Trans t)
            throws Exception
    {
        l.info("create " + PathCombo.toLogString(pcPhysical._path));

        // create the object
        SOID soidParent = _ds.resolveThrows_(pcPhysical._path.removeLast());
        OA oaParent = _ds.getOANullable_(soidParent);
        if (oaParent.isExpelled()) {
            // after the false return, scanner or linker should create the parent and recurse down
            // to the child again. exact implementations depend on operating systems.
            l.warn("parent is expelled. ignore child creation");
            return false;
        } else {
           if (oaParent.isAnchor()) soidParent = _ds.followAnchorThrows_(oaParent);
           _oc.create_(dir ? Type.DIR : Type.FILE, soidParent, pcPhysical._path.last(), MAP, t);
           return true;
        }
    }

    private void detectAndApplyModification_(SOID soid, String absPath, Trans t)
            throws IOException, ExNotFound, SQLException
    {
        OA oa = _ds.getOANullable_(soid);
        assert oa.isFile();
        CA caMaster = oa.caMaster();

        boolean modified;
        if (caMaster == null) {
            // The master CA is absent. This may happen when a file's metadata has been downloaded
            // but the content hasn't been so. Create the master CA in this case.
            l.warn("absent master CA on " + soid + ". create it");
            _ds.createCA_(soid, KIndex.MASTER, t);
            modified = true;
        } else {
            InjectableFile f = _factFile.create(absPath);
            modified = f.wasModifiedSince(caMaster.mtime(), caMaster.length());
        }

        if (modified) {
            l.info("modify " + soid);
            _cm.atomicWrite_(new SOCKID(soid, CID.CONTENT), t);
            _a.incSaveCount();
        }
    }
}

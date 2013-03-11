package com.aerofs.daemon.core.linker;

import java.io.IOException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

import static com.aerofs.daemon.core.linker.MightCreateOperations.Operation.*;
import static com.aerofs.daemon.core.linker.MightCreateOperations.*;
import static com.aerofs.lib.obfuscate.ObfuscatingFormatters.obfuscatePath;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.lib.Param;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import org.slf4j.Logger;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.google.inject.Inject;

import javax.annotation.Nullable;

/**
 * This class is responsible for maintaining an accurate physical<->logical object mapping
 *
 * It can be called in a single-shot way upon receiving a file system notification or repeatedly
 * in the context of a larger {@link com.aerofs.daemon.core.linker.scanner.ScanSession}.
 *
 * Internally, this class only performs the logic that determines what operation is required to
 * restore the object mapping if it was broken. All the actual filesystem and database modification
 * is delegated to {@link MightCreateOperations}. This design was chosen to enforce a stricter
 * separation of responsibility which makes it easier to test and maintain the code.
 *
 * Some vocabulary used throughout this class and {@link MightCreateOperations}:
 *
 * target path: path passed to MightCreate
 * target SOID: SOID to which the target path resolves
 * source SOID: SOID associated to the FID of the target path
 * source path: path to which the source SOID resolves
 */
public class MightCreate
{
    private static Logger l = Loggers.getLogger(MightCreate.class);

    private final IgnoreList _il;
    private final DirectoryService _ds;
    private final InjectableDriver _dr;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;
    private final SharedFolderTagFileAndIcon _sfti;
    private final MightCreateOperations _mcop;

    @Inject
    public MightCreate(IgnoreList ignoreList, DirectoryService ds, InjectableDriver driver,
            SharedFolderTagFileAndIcon sfti, CfgAbsRootAnchor cfgAbsRootAnchor,
            MightCreateOperations mcop)
    {
        _il = ignoreList;
        _ds = ds;
        _dr = driver;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
        _sfti = sfti;
        _mcop = mcop;
    }

    public static enum Result {
        NEW_OR_REPLACED_FOLDER, // the physical object is a folder and a new,
                                // corresponding logical object has been created
        EXISTING_FOLDER,        // the physical object is a folder and the corresponding
                                // logical object already exists before the method call
        FILE,                   // the physical object is a file
        IGNORED,                // the physical object is ignored
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

        // See class-level comment for vocabulary definitions
        SOID sourceSOID = _ds.getSOIDNullable_(fnt._fid);
        SOID targetSOID = _ds.resolveNullable_(pcPhysical._path);

        Path targetPath = pcPhysical._path;
        Path sourcePath = sourceSOID != null ? _ds.resolveNullable_(sourceSOID) : null;

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
        if (sourcePath != null && detectHardLink_(sourcePath, targetPath, fnt._fid)) {
            return Result.IGNORED;
        }

        Set<Operation> ops = determineUpdateOperation_(sourceSOID, targetSOID, fnt);

        boolean createdOrReplaced = _mcop.executeOperation_(ops, sourceSOID, targetSOID, pcPhysical,
                fnt, delBuffer, t);

        l.info(pcPhysical + " " + ops + " " + createdOrReplaced);

        if (fnt._dir) {
            return createdOrReplaced ? Result.NEW_OR_REPLACED_FOLDER : Result.EXISTING_FOLDER;
        } else {
            return Result.FILE;
        }
    }

    /**
     * Delete invalid tag file/folder
     */
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
     * Determine whether a hardlink exists between {@code sourcePath} and {@code targetPath}.
     * @param sourcePath path in the DB when looking up {@code physicalFID}
     * @param targetPath path that currently represents {@code physicalFID} on the FS
     * @return whether a hardlink has been detected
     */
    private boolean detectHardLink_(Path sourcePath, Path targetPath, FID physicalFID)
            throws SQLException
    {
        // If the logical and physical paths are not equal and the
        // physical file with that logical path has the same FID as the current file
        // then we detected a hard link between the logical object and the current
        // physical object.

        if (targetPath.equalsIgnoreCase(sourcePath)) return false;
        // Do a case insensitive equals to tolerate both case preserving and insensitive
        // file systems to make sure we don't ignore mv operations on files that have
        // the same letters in their name.
        // Example scenario is: mv ./hello.txt ./HELLO.txt

        try {
            String absLogicalPath = sourcePath.toAbsoluteString(_cfgAbsRootAnchor.get());
            FID logicalFID = _dr.getFID(absLogicalPath);

            return logicalFID != null && logicalFID.equals(physicalFID);
        } catch (IOException e) {
            // File is not found or there was an IO exception, in either case
            // this means that the FID of the logical object was not found, most likely
            // deleted, so proceed to update the DB.
            return false;
        }
    }

    /**
     * Determine update operation(s) needed to ensure that the target physical object is mapped to
     * an up-to-date logical object
     *
     * @param sourceSOID logical object whose FID coincide with the target physical object, if any
     * @param targetSOID logical object whose path coincide with the target physical object, if any
     * @return update operations required to bring the logical mapping up-to-date
     */
    private Set<Operation> determineUpdateOperation_(@Nullable SOID sourceSOID,
            @Nullable SOID targetSOID, FIDAndType fnt) throws SQLException
    {
        if (targetSOID == null) return EnumSet.of(sourceSOID == null ? Create : Update);

        OA targetOA = _ds.getOA_(targetSOID);
        Set<Operation> ops = shouldRenameTarget_(targetOA, fnt._dir);
        if (ops != null) {
            // The logical object has a different type or is expelled and thus can't link to the
            // physical object by replacing the FID.
            // NB: do not remove the target object from the deletion buffer, so it will be deleted
            // if it's not moved to other locations later on.
            ops.add(sourceSOID == null || targetSOID.equals(sourceSOID) ? Create : Update);
            return ops;
        } else if (targetSOID.equals(sourceSOID)) {
            return EnumSet.of(Update);
        } else {
            // The assertion below is guaranteed by the above code. N.B. oa.fid() may be null if
            // the no branch is present. See also detectAndApplyModification_()
            assert !fnt._fid.equals(targetOA.fid());
            return EnumSet.of(Replace);
        }
    }

    /**
     * @param targetOA logical object currently mapped to the target path
     * @param dir whether the target physical object is a directory
     * @return extra operations if the target needs to be renamed, null otherwise
     */
    private @Nullable Set<Operation> shouldRenameTarget_(OA targetOA, boolean dir)
            throws SQLException
    {
        if (targetOA.isExpelled()) return EnumSet.of(RenameTarget);
        // This assertion would fail if a new type other than file/dir/anchor is created in
        // the future. The assertion is needed for the "fnt._dir == ...isDirOrAnchor()"
        // check below.
        assert targetOA.isDirOrAnchor() != targetOA.isFile();
        return targetOA.isDirOrAnchor() == dir ? null : EnumSet.of(RenameTarget, RandomizeFID);
    }
}

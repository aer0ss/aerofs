package com.aerofs.daemon.core.phy.linked.linker;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.Operation.*;
import static com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations.*;
import static com.google.common.base.Preconditions.checkArgument;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ex.ExFileNoPerm;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.Sets;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class is responsible for maintaining an accurate physical<->logical object mapping
 *
 * It can be called in a single-shot way upon receiving a file system notification or repeatedly
 * in the context of a larger {@link com.aerofs.daemon.core.phy.linked.linker.scanner.ScanSession}.
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
    private final static Logger l = Loggers.getLogger(MightCreate.class);

    private final IgnoreList _il;
    private final DirectoryService _ds;
    private final InjectableDriver _dr;
    private final SharedFolderTagFileAndIcon _sfti;
    private final MightCreateOperations _mcop;
    private final LinkerRootMap _lrm;
    private final ILinkerFilter _filter;
    private final RepresentabilityHelper _rh;
    private final IOSUtil _osutil;
    private final RockLog _rocklog;

    @Inject
    public MightCreate(IgnoreList ignoreList, DirectoryService ds, InjectableDriver driver,
            SharedFolderTagFileAndIcon sfti, MightCreateOperations mcop, LinkerRootMap lrm,
            ILinkerFilter filter, RepresentabilityHelper rh, IOSUtil osutil, RockLog rocklog)
    {
        _il = ignoreList;
        _ds = ds;
        _dr = driver;
        _sfti = sfti;
        _mcop = mcop;
        _lrm = lrm;
        _filter = filter;
        _rh = rh;
        _osutil = osutil;
        _rocklog = rocklog;
    }

    public static enum Result {
        NEW_OR_REPLACED_FOLDER, // the physical object is a folder and a new,
                                // corresponding logical object has been created
        EXISTING_FOLDER,        // the physical object is a folder and the corresponding
                                // logical object already exists before the method call
        FILE,                   // the physical object is a file
        IGNORED,                // the physical object is ignored
    }

    public boolean shouldIgnoreChildren_(PathCombo pc, @Nullable SOID parent) throws SQLException
    {
        OA oaParent = parent == null ? null : _ds.getOANullable_(parent);
        if (oaParent == null || oaParent.isExpelled()) {
            // if we get a notification about a path for which the parent is expelled or not
            // present, we are most likely hitting a race between expulsion and creation so we
            // should ignore the notification and wait for the one about the new parent, which
            // will in turn trigger a scan
            l.warn("expel/create race under {}", parent);
            return true;
        }
        return _filter.shouldIgnoreChilren_(pc, oaParent);
    }

    /**
     * N.B. This method must throw on _any_ error instead of ignoring them and proceeding. See
     * Comment (A) in ScanSession.
     *
     * @param pcPhysical the path of the physical file
     * @throws com.aerofs.base.ex.ExNotFound if either the physical object or the parent of the
     * logical object is not found when creating or moving the object.
     */
    public Result mightCreate_(PathCombo pcPhysical, IDeletionBuffer delBuffer, OIDGenerator og,
            Trans t) throws Exception
    {
        if (deleteIfInvalidTagFile(pcPhysical) || _il.isIgnored(pcPhysical._path.last())) {
            return Result.IGNORED;
        }

        if (_osutil.isInvalidFileName(pcPhysical._path.last())) {
            l.error("inconsistent encoding validity: {}", pcPhysical._path.last());
            _rocklog.newDefect("mc.invalid")
                    .addData("path", pcPhysical._path.last())
                    .send();
            return Result.IGNORED;
        }

        @Nullable FIDAndType fnt = null;
        try {
            fnt = _dr.getFIDAndTypeNullable(pcPhysical._absPath);
            // TODO: report to UI if FID null (symlink or special file)
        } catch (ExFileNoPerm e) {
            // TODO: report to UI
            l.warn("no perm {}", pcPhysical);
            _rocklog.newDefect("mc.fid.noperm")
                    .send();
        } catch (ExFileNotFound e) {
            l.warn("not found {}", pcPhysical);
            _rocklog.newDefect("mc.fid.notfound")
                    .send();
        } catch (Exception e) {
            l.warn("could not determine fid {}", pcPhysical);
            _rocklog.newDefect("mc.fid.exception")
                    .setException(e)
                    .send();
        }

        // ignore files for which we cannot get an FID
        if (fnt == null) return Result.IGNORED;

        SOID parent = _ds.resolveNullable_(pcPhysical._path.removeLast());
        if (shouldIgnoreChildren_(pcPhysical,  parent)) {
            l.debug("ignored {}:{}", pcPhysical, fnt._fid);
            return Result.IGNORED;
        }

        l.debug("{}:{} under {}", pcPhysical, fnt._fid, parent);

        // See class-level comment for vocabulary definitions
        @Nullable SOID sourceSOID = _ds.getSOIDNullable_(fnt._fid);
        @Nullable SOID targetSOID = _ds.resolveNullable_(pcPhysical._path);

        @Nonnull  Path targetPath = pcPhysical._path;
        @Nullable Path sourcePath = sourceSOID != null ? _ds.resolveNullable_(sourceSOID) : null;

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
            // TODO: report to UI
            l.info("ignore hardlink {}<->{}",
                    ObfuscatingFormatters.obfuscatePath(sourcePath),
                    ObfuscatingFormatters.obfuscatePath(targetPath));
            return Result.IGNORED;
        }

        Set<Operation> ops = determineUpdateOperation_(pcPhysical, sourceSOID, targetSOID, fnt);

        // reduce log volume by ignoring update op on existing object
        if (!(sourceSOID != null && sourceSOID.equals(targetSOID) && ops.equals(EnumSet.of(UPDATE)))) {
            l.info("{} {} {} {}", pcPhysical, ops, sourceSOID, targetSOID);
        }

        boolean createdOrReplaced = _mcop.executeOperation_(ops, sourceSOID, targetSOID, pcPhysical,
                fnt, delBuffer, og, t);

        if (fnt._dir) {
            return createdOrReplaced ? Result.NEW_OR_REPLACED_FOLDER : Result.EXISTING_FOLDER;
        } else {
            return Result.FILE;
        }
    }

    /**
     * Delete invalid tag file/folder
     */
    private boolean deleteIfInvalidTagFile(PathCombo pc) throws IOException, SQLException
    {
        if (!pc._path.last().equals(LibParam.SHARED_FOLDER_TAG)) return false;

        // Remove any invalid tag file (i.e tag file under non-anchor)
        SOID parent = _ds.resolveNullable_(pc._path.removeLast());
        if (parent != null) {
            OA oa = _ds.getOA_(parent);
            // inside the default root resolveNullable_ will find an anchor, in an external root
            // it will find the root dir
            if (!(oa.isAnchor() || oa.soid().oid().isRoot())) {
                _sfti.deleteTagFileAndIconIn(new File(pc._absPath).getParent());
            }
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
    {
        // If the source and target paths are not equal but the corresponding FIDs are equal then
        // we detected a hard link
        //
        // However we have to be careful to ignore cases where the two filenames are equivalent
        // on the underlying physical storage (e.g. if case-insensitive or unicode-normalizing)

        if (_lrm.isPhysicallyEquivalent_(targetPath, sourcePath)) {
            return false;
        }

        try {
            String absRootAnchor = _lrm.absRootAnchor_(sourcePath.sid());
            if (absRootAnchor == null) return false;

            String absLogicalPath = sourcePath.toAbsoluteString(absRootAnchor);
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
    private Set<Operation> determineUpdateOperation_(@Nonnull PathCombo pc,
            @Nullable SOID sourceSOID, @Nullable SOID targetSOID, @Nonnull FIDAndType fnt)
            throws SQLException
    {
        @Nullable OA sourceOA = sourceSOID != null ? _ds.getOA_(sourceSOID) : null;
        boolean sourceSameType = sourceOA != null && sourceOA.isDirOrAnchor() == fnt._dir;

        if (targetSOID == null) return updateSource(sourceSOID, sourceSameType);

        OA targetOA = _ds.getOA_(targetSOID);

        // It is possible to run into a non-representable object during a scan, e.g. :
        //
        // virtual:
        //      foo/
        //          bar
        //          BAR
        //
        // physical:
        //      foo/
        //          bar
        //      .aerofs.aux.<SID>/
        //          <soid:BAR>
        //
        // user deletes "bar" and create new "BAR" before AeroFS picks up deletion (either because
        // AeroFS is not running or because the creation happens before the "bar" leaves the
        // TimeoutDeletionBuffer).
        //
        // The only safe way to deal with such corner cases is to rename the NRO
        if (_rh.isNonRepresentable_(targetOA)) {
            l.info("nro in mc {} {}", targetSOID, _rh.getConflict_(targetSOID));
            return Sets.union(renameTargetAndUpdateSource(sourceSOID, sourceSameType),
                    EnumSet.of(NON_REPRESENTABLE_TARGET));
        }

        if (sourceSameType && targetSOID.equals(sourceSOID)) return EnumSet.of(UPDATE);

        if (canSafelyReplaceFID(pc, sourceSOID, targetOA, fnt._dir)) {
            // The assertion below is guaranteed by the above code.
            // N.B: targetOA.fid() may be null if no branch is present.
            checkArgument(!fnt._fid.equals(targetOA.fid()), "%s %s %s %s %s %s", fnt._fid, fnt._dir,
                    sourceSameType, sourceSOID, targetSOID,
                    sourceOA == null ? null : sourceOA.isDirOrAnchor());
            return EnumSet.of(REPLACE);
        }

        // The logical object can't simply link to the physical object by replacing the FID.
        return renameTargetAndUpdateSource(sourceSOID, sourceSameType);
    }

    private static Set<Operation> updateSource(SOID source, boolean sourceSameType)
    {
        if (source == null) return EnumSet.of(CREATE);
        if (sourceSameType) return EnumSet.of(UPDATE);
        // same FID, different types
        // This may happen if either:
        //    1) the OS deletes an object and soon reuses the same FID to create a new object
        //       of a different type. This has been observed on a Ubuntu test VM.
        //    2) the filesystems has ephemeral FIDs, such as FAT on Linux.
        // In either case, we need to assign the logical object with a random FID
        return EnumSet.of(CREATE, RANDOMIZE_SOURCE_FID);
    }

    private static Set<Operation> renameTargetAndUpdateSource(SOID source, boolean sourceSameType)
    {
        return Sets.union(EnumSet.of(RENAME_TARGET), updateSource(source, sourceSameType));
    }

    /**
     * For locally present files, we can always safely use the Replace operation that adjusts the
     * FID of the target and update the master CA if necessary
     *
     * Folders are trickier to handle and shared folders especially so.
     *
     * Consider for instance, 2 anchors:
     *    o1:foo
     *      \-> baz
     *    o2:bar
     *      \-> qux
     *
     * Now the user deletes bar and rename foo to bar. The expected result is:
     *    o1:bar
     *      \-> baz
     *    sp.leave(o2)
     *
     * But allowing a replace operation on folders would lead to
     *    o2:bar
     *      \-> baz
     *    sp.leave(o1)
     * i.e. we would leave the wrong shared folder, mistakenly delete all files previously under
     * o2:bar and replace them with files previously under o1:foo
     *
     * Not only is this unintuitive but from a user's perspective it is a spurious deletion
     * for remote users and an ACL "breach" for the local user.
     *
     * In the case of shared folders however we can exploit the presence of the .aerofs tag file
     * to check whether the OA and the physical folder match despite the FIDs suggesting they don't.
     * This is important to avoid breaking shared folders on filesystems with ephemeral FIDs.
     *
     * Regular folders can still be eligible for FID replacement provided that the new FID is not
     * associated with any existing SOID (i.e. sourceSOID == null). This condition is required
     * otherwise handling files and sub-folders under this folder would be a mess.
     */
    private boolean canSafelyReplaceFID(PathCombo pc, @Nullable SOID sourceSOID, OA target,
            boolean dir)
    {
        if (target.isExpelled() || target.isDirOrAnchor() != dir) {
            // Don't replace if the target is expelled or the types don't match.
            return false;

        } else if (target.isAnchor()) {
            // If the tag file matches the anchor we can replace the FID
            return _sfti.isSharedFolderRoot(SID.anchorOID2storeSID(target.soid().oid()),
                    pc._absPath);

        } else if (target.isDir()) {
            return sourceSOID == null;

        } else {
            checkArgument(target.isFile());
            return true;
        }
    }
}

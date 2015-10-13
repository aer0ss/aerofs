/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.BaseCfg;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.defects.Defects.newDefectWithLogsNoCfg;
import static com.aerofs.lib.cfg.ICfgStore.ROOT;

public abstract class RootAnchorUtil
{
    private RootAnchorUtil()
    {
        // private to enforce uninstantiability
    }

    /**
     * Test if a given anchor root is valid. Use the format of 'e.getMessage() + ". More message."'
     * to display the exception thrown by this method.
     *
     * @param allowNonEmptyFolder whether to allow the root anchor to be non-empty if it already
     * exists.
     *
     * @throws com.aerofs.lib.ex.ExNotDir if the root anchor path points to a file.
     * @throws com.aerofs.base.ex.ExAlreadyExist if the root anchor is nonempty
     * @throws com.aerofs.base.ex.ExNoPerm if AeroFS cannot read or write to root anchor
     * @throws com.aerofs.lib.ex.ExUIMessage if root anchor filesystem is not supported
     * @throws com.aerofs.base.ex.ExBadArgs if the root anchor is the rtroot
     */
    public static void checkRootAnchor(String rootAnchor, @Nonnull String rtRoot,
            StorageType storageType, boolean allowNonEmptyFolder)
            throws IOException, ExNoPerm, ExNotDir, ExAlreadyExist, ExUIMessage, ExBadArgs
    {
        File fRootAnchor = new File(rootAnchor);

        if (fRootAnchor.getAbsolutePath().equals(new File(rtRoot).getAbsolutePath())) {
            throw new ExBadArgs("The root anchor must not be the same as the rtroot.");
        }

        // Check if it's a file or a non-empty folder
        if (fRootAnchor.isFile()) {
            throw new ExNotDir("A file at the desired location {} already exists", fRootAnchor);

        } else if (!allowNonEmptyFolder) {
            String[] children = fRootAnchor.list();
            // children is null if fRootAnchor is not a directory.

            // NOTE: (GS) This can be a problem on OS X and Windows since it's very likely that the
            // user has .DS_Store, Icon\r, desktop.ini, Thumbs.db, or some other hidden system file.
            // We should probably do something to handle those gracefully.
            if (children != null && children.length > 0) {
                throw new ExAlreadyExist(rootAnchor + " is a non-empty folder");
            }
        }

        File fToCheck = fRootAnchor.exists() ? fRootAnchor : fRootAnchor.getParentFile();

        // Check if we have read and write permissions
        // Don't bother checking on Windows because:
        //   - Java reports both false positives and false negatives on Windows
        //   - The file picker dialog will warn the user if he tries to pick a directory to which
        //     he doesn't have permissions
        if (!OSUtil.isWindows()) {
            if (!fToCheck.canRead()) throwExNoPerm(Perm.READ, fToCheck);
            if (!fToCheck.canWrite()) throwExNoPerm(Perm.WRITE, fToCheck);
        }

        // Check if it's a supported filesystem. We only support filesystems that have persistent
        // i-node numbers. This is to allow the linker to work properly.
        // This is not needed for Multiuser.
        if (storageType == StorageType.LINKED && Cfg.useFSTypeCheck(rtRoot)) {
            checkFilesystemType(rtRoot, fToCheck);
        }
    }

    private static void checkFilesystemType(String rtRoot, File fToCheck)
            throws IOException, ExUIMessage
    {
        OutArg<Boolean> remote = new OutArg<Boolean>();
        String type = OSUtil.get().getFileSystemType(fToCheck.getAbsolutePath(), remote);
        if (unsupportedFsBetterNames.containsKey(type)) {
                type = unsupportedFsBetterNames.get(type);
        }
        boolean supported = OSUtil.get().isFileSystemTypeSupported(type.toUpperCase(), remote.get());
        if (!supported) {
            String r = remote.get() != null && remote.get() ? "remote " : "";
            // sync instead of async to make sure we get it
            newDefectWithLogsNoCfg("file_system.type", UserID.UNKNOWN, rtRoot)
                    .setMessage("unsupported fs: " + r + type)
                    .sendSyncIgnoreErrors();

            throw new ExUIMessage(L.product() + " doesn't support " + r + type +
                    " filesystems on " + OSUtil.getOSName() + " at this moment");
        }
    }

    public static File cleanAuxRootForPath(String rootAnchor, SID sid)
    {
        File dir = new File(BaseCfg.absAuxRootForPath(rootAnchor, sid));
        FileUtil.deleteIgnoreErrorRecursively(dir);
        return dir;
    }

    private enum Perm {READ, WRITE}
    private static void throwExNoPerm(Perm perm, File file) throws ExNoPerm
    {
        throw new ExNoPerm(L.product() + " doesn't have sufficient permissions to " +
                perm.toString().toLowerCase() + " files under " + file);
    }

    // We can add additional user-friendly name mappings, in case more users happen to
    // try to use filesystems that stat doesn't know about.
    private static Map<String, String> unsupportedFsBetterNames = ImmutableMap.of(
            "UNKNOWN (0xf15f)", "ecryptfs");

    /**
     * Test if a new anchor root is valid against the old path. Use the format of
     * 'e.getMessage() + ". More message."' to display the exception thrown by this method.
     *
     * @param rootOld can be relative
     * @param rootNew can be relative
     */
    public static void checkNewRootAnchor(String rootOld, String rootNew)
            throws ExBadArgs, IOException, SQLException
    {
        String canonOldRoot = new File(rootOld).getCanonicalPath();
        String canonNewRoot = new File(rootNew).getCanonicalPath();

        checkNewCanonRoot(canonNewRoot, canonOldRoot, "the old location");

        for (String absPath : Cfg.getRoots().values()) {
            checkNewCanonRoot(canonNewRoot, absPath, "an existing root");
        }
    }

    private static void checkNewCanonRoot(String canonNewRoot, String canonExistingRoot,
            String locationLabel) throws ExBadArgs, IOException
    {
        if (canonExistingRoot.equals(canonNewRoot)) {
            throw new ExBadArgs("The new location is identical to " + locationLabel);
        }

        if (contains(canonNewRoot, canonExistingRoot)) {
            throw new ExBadArgs("The new location overlaps with " + locationLabel);
        }
    }

    /**
     * Checks whether the path p2 is an ancestor directory of path p1
     */
    private static boolean contains(String p1, String p2)
    {
        assert !p1.endsWith(File.separator) && !p2.endsWith(File.separator);
        return p1.length() > p2.length() && p1.startsWith(p2) &&
                p1.charAt(p2.length()) == File.separatorChar;
    }

    /**
     * @param root may be relative
     * @return an adjusted, absolute root anchor path
     */
    public static String adjustRootAnchor(String root, @Nullable SID sid)
    {
        root = new File(root).getAbsolutePath();
        root = Util.removeTailingSeparator(root);

        // only enforce branded root anchor suffix for default root
        if (sid != null) return root;

        String rootAnchorName = L.rootAnchorName();
        if (!root.toLowerCase().endsWith(File.separator + rootAnchorName.toLowerCase())) {
            root += File.separator + rootAnchorName;
        }
        return root;
    }

    public static void updateAbsRootCfg(@Nullable SID sid, String newAbsPath) throws SQLException
    {
        if (sid == null) {
            String oldAbsPath = Cfg.db().get(ROOT);

            Cfg.db().set(ROOT, newAbsPath);
            if (Cfg.storageType() == StorageType.LINKED) {
                // when using linked storage we need to update implicit root(s)

                if (L.isMultiuser()) {
                    // linked storage in multiuser may have many implicit roots
                    for (Entry<SID, String> e : Cfg.getRoots().entrySet()) {
                        moveRootIfUnder(e.getKey(), e.getValue(), oldAbsPath, newAbsPath);
                    }
                } else {
                    // in singleuser mode the user root store is the only implicit root
                    Cfg.moveRoot(Cfg.rootSID(), newAbsPath);
                }
            }
        } else {
            assert Cfg.storageType() == StorageType.LINKED;
            Cfg.moveRoot(sid, newAbsPath);
        }
    }

    private static void moveRootIfUnder(SID sid, String absRoot,
            String oldParentRoot, String newParentRoot) throws SQLException {
        if (Path.isUnder(oldParentRoot, absRoot)) {
            int len = oldParentRoot.length();
            String rootRelPath = absRoot.substring(len +
                    (absRoot.charAt(len) == File.separatorChar ? 1 : 0));

            Cfg.moveRoot(sid, Util.join(newParentRoot, rootRelPath));
        }
    }
}

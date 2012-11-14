/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;

import java.io.File;
import java.io.IOException;

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
     * @throws com.aerofs.lib.ex.ExAlreadyExist if the root anchor is nonempty
     * @throws com.aerofs.lib.ex.ExNoPerm if AeroFS cannot read or write to root anchor
     * @throws com.aerofs.lib.ex.ExBadArgs if root anchor filesystem is not supported
     */
    public static void checkRootAnchor(String rootAnchor, String rtRoot,
            boolean allowNonEmptyFolder)
            throws IOException, ExNoPerm, ExNotDir, ExAlreadyExist, ExBadArgs
    {
        File fRootAnchor = new File(rootAnchor);

        // Check if it's a file or a non-empty folder
        if (fRootAnchor.isFile()) {
            throw new ExNotDir("A file at the desired location " + fRootAnchor +
                    " already exists");

        } else if (!allowNonEmptyFolder) {
            String[] children = fRootAnchor.list();
            // children is null if fRootAnchor is not a directory.
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
            String msg = S.PRODUCT + " doesn't have sufficient permissions to ";
            if (!fToCheck.canRead()) throw new ExNoPerm(msg + "read files under " + fToCheck);
            if (!fToCheck.canWrite()) throw new ExNoPerm(msg + "write files under " + fToCheck);
        }

        // Check if it's a supported file system
        if (Cfg.useFSTypeCheck(rtRoot)) {
            OutArg<Boolean> remote = new OutArg<Boolean>();
            String type = OSUtil.get().getFileSystemType(fToCheck.getAbsolutePath(), remote);
            boolean supported = OSUtil.get().isFileSystemTypeSupported(type.toUpperCase(), remote.get());
            if (!supported) {
                String r = remote.get() != null && remote.get() ? "remote " : "";
                // sync instead of async to make sure we get it
                SVClient.logSendDefectSyncNoCfgIgnoreErrors(true, "unsupported fs: " + r + type,
                        null, "n/a", rtRoot);

                throw new ExBadArgs(S.PRODUCT + " doesn't support " + r + type +
                        " filesystems on " + OSUtil.getOSName() + " at this moment");
            }
        }
    }

    /**
     * Test if a new anchor root is valid against the old path. Use the format of
     * 'e.getMessage() + ". More message."' to display the exception thrown by this method.
     *
     * @param rootOld can be relative
     * @param rootNew can be relative
     */
    public static void checkNewRootAnchor(String rootOld, String rootNew)
            throws ExBadArgs, IOException
    {
        String canonOldRoot = new File(rootOld).getCanonicalPath();
        String canonNewRoot = new File(rootNew).getCanonicalPath();

        if (canonOldRoot.equals(canonNewRoot)) {
            throw new ExBadArgs("The new location is identical to the old location");
        }

        if (contains(canonNewRoot, canonOldRoot)) {
            throw new ExBadArgs("The new location overlaps with the old location");
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
    public static String adjustRootAnchor(String root)
    {
        root = new File(root).getAbsolutePath();
        root = Util.removeTailingSeparator(root);
        if (!root.toLowerCase().endsWith(File.separator + S.ROOT_ANCHOR_NAME.toLowerCase())) {
            root += File.separator + S.ROOT_ANCHOR_NAME;
        }

        return root;
    }
}

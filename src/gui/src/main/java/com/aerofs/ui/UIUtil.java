package com.aerofs.ui;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExPasswordExpired;
import com.aerofs.ids.SID;
import com.aerofs.controller.ExLaunchAborted;
import com.aerofs.controller.Launcher;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.notif.NotifMessage;
import com.aerofs.gui.setup.DlgJoinSharedFolders;
import com.aerofs.gui.setup.DlgTutorial;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.launch_tasks.ULTRtrootMigration;
import com.aerofs.ui.launch_tasks.ULTRtrootMigration.ExFailedToMigrate;
import com.aerofs.ui.launch_tasks.ULTRtrootMigration.ExFailedToReloadCfg;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class UIUtil
{
    static final Logger l = Loggers.getLogger(UIUtil.class);

    public static boolean isSystemFile(PBPath path)
    {
        for (String elem : path.getElemList()) {
            if (elem.equals(ClientParam.TRASH)) return true;
        }
        return false;
    }

    public static boolean shallHide(PBPath path)
    {
        int cnt = path.getElemCount();
        if (cnt >= 1) {
            String name = path.getElem(cnt - 1);
            if (name.startsWith(".") || name.startsWith("~") ||
                    name.endsWith(".tmp")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Schedule unlink and exit (via the command server).
     *
     * This is called locally when the user requests that we unlink the device. This must be done
     * via the command server so the device is properly cleaned up on the server and on other peer
     * devices.
     */
    public static void scheduleUnlinkAndExit()
            throws Exception
    {
        newMutualAuthClientFactory().create()
                .signInRemote()
                .unlinkDevice(BaseUtil.toPB(Cfg.did()), false);
    }

    private static String getUserFriendlyID(PBSOCID pbsocid)
    {
        ByteString oid = pbsocid.getOid();
        // don't print the 0th and 1st bytes to avoid exposing the actual oid
        return String.format("%1$X%2$02X", oid.byteAt(1) & 0xF, oid.byteAt(2));
    }

    /**
     * @param path the result of new Path(pbpath)
     */
    public static String getUserFriendlyPath(PBSOCID pbsocid, PBPath pbpath, Path path)
    {
        String text;
        if (path.isEmpty() || UIUtil.isSystemFile(pbpath)) {
            text = "metadata (" + getUserFriendlyID(pbsocid) + ")";
        } else if (pbsocid.getCid() == CID.META.getInt()) {
            text = "metadata (" + getUserFriendlyID(pbsocid) + ") for " + path;
        } else {
            text = path.toStringRelative();
        }

        return text;
    }

    /**
     * Returns a pair of strings with the default first name and last name of the user.
     * We do that by splitting the user.name property on the first space character
     * If there are no spaces, then the first element of the pair will be set with the name, and the
     * second element will be an empty string.
     */
    public static FullName getDefaultFullName()
    {
        String userName = System.getProperty("user.name").trim();
        int spacePos = userName.indexOf(' ');
        if (spacePos > 0) {
            return new FullName(userName.substring(0, spacePos),
                    userName.substring(spacePos + 1));
        } else {
            return new FullName(userName, "");
        }
    }

    private static String LAUNCH_ERROR_STRING = "Sorry, " + L.product() + " couldn't launch." +
            " Please contact us at " + WWW.SUPPORT_EMAIL_ADDRESS;

    /**
     * Helper method to perform launch for the Java UIs (ie: CLI and GUI)
     * The flow here is:
     *  - CLI or GUI call UIUtil.launch() to perform common launch logic
     *  - UIUtil.launchImpl() call Controller methods
     *  - Controller methods then call Setup or Launcher methods
     *
     * N.B. preLaunch and postLaunch are intended to be different set of tasks to be performed on
     * either GUI or CLI but not both.
     *
     * FIXME(AT) despite what the method signature states and suggests, preLaunch isn't executed
     * before Launcher.launch().
     *  @param preLaunch a runnable that will be executed in the UI thread
     * @param postLaunch a runnable that will be executed in the UI thread, iff the launch succeeds
     */
    public static void launch(String rtRoot, Runnable preLaunch, Runnable postLaunch)
    {
        // N.B. migrateRtroot _must_ be done before we run launchImpl because launchImpl needs
        // the rtroot to be able to determine whether we need to run setup.
        migrateRtroot(rtRoot);

        launchImpl(rtRoot, preLaunch, postLaunch);
    }

    private static void launchImpl(String rtRoot, Runnable preLaunch, Runnable postLaunch)
    {
        try {
            if (needsSetup()) {
                setup(rtRoot, preLaunch, postLaunch);
            } else {
                scheduleLaunch(rtRoot, preLaunch, postLaunch);
            }
        } catch (ExLaunchAborted e) {
            // User clicked on cancel, exit without error messages
            ExitCode.NORMAL_EXIT.exit();
        } catch (Exception e) {
            logAndShowLaunchError(e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }
    }

    private static boolean needsSetup()
    {
        try {
            return Launcher.needsSetup();
        } catch (Exception e) {
            UI.get().show(MessageType.ERROR, e.getLocalizedMessage() != null ? e.getLocalizedMessage()
                    : "Sorry, an internal error happened, preventing " + L.product() + " to launch");
            ExitCode.FATAL_ERROR.exit();
            return false;
        }
    }

    /**
     * Note that using launchImpl() with preLaunch parameter doesn't work in the case of rtroot
     * migration for 2 reasons:
     *
     * 1. preLaunch in launchImpl() doesn't work because in that case, preLaunch doesn't necessarily
     * execute before Launcher.launch().
     * 2. preLaunch in launchImpl() is intended to do tasks that are _different_ between GUI and
     * CLI, whereas rtroot migration is a common task between GUI and CLI.
     *
     * @param rtRoot - the path to rtRoot.
     * @pre UI.get() is set.
     */
    public static void migrateRtroot(String rtRoot)
    {
        Preconditions.checkArgument(UI.get() != null);

        ULTRtrootMigration task = new ULTRtrootMigration(L.productSpaceFreeName(), rtRoot);

        try {
            task.run();
        } catch (ExFailedToMigrate e) {
            newDefectWithLogs("rtroot_migration")
                    .setMessage("failed to migrate rtroot")
                    .setException(e)
                    .sendSyncIgnoreErrors();

            // Since ErrorMessages doesn't support messages that depends on additional
            // information in the exception instance, default message is used.
            String message = L.product() + " is unable to upgrade your user data to the " +
                    "new format. Please manually move the folder\n\n" +
                    "\"" + e._oldRtrootPath + "\"\n\n" +
                    "to\n\n" +
                    "\"" + e._newRtrootPath + "\"\n\n";
            ErrorMessages.show(e, message);

            ExitCode.FAIL_TO_LAUNCH.exit();
        } catch (ExFailedToReloadCfg e) {
            // this is similar to the message on Main.main() when Cfg fails to load
            newDefectWithLogs("rtroot_migration.reload_cfg")
                    .setMessage("failed to reload cfg after rtroot migration")
                    .setException(e)
                    .sendSyncIgnoreErrors();

            String message = L.product() + " is unable to launch.";
            ErrorMessages.show(e, message);

            ExitCode.FAIL_TO_LAUNCH.exit();
        }
    }

    private static void scheduleLaunch(final String rtRoot, Runnable preLaunch,
            final Runnable postLaunch)
    {
        if (preLaunch != null) { UI.get().asyncExec(preLaunch); }
        ThreadUtil.startDaemonThread("launch", () -> {
            if (launchInWorkerThread()) {
                finishLaunch(postLaunch);
            } else {
                // we get there if the user choose to reinstall due to a tampered/corrupted DB
                UI.get().asyncExec(() -> launchImpl(rtRoot, null, postLaunch));
            }
        });
    }

    private static boolean launchInWorkerThread()
    {
        try {
            Launcher.launch(false);
        } catch (ExNotSetup e) {
            return false;
        } catch (Exception e) {
            logAndShowLaunchError(e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }
        return true;
    }

    private static void setup(String rtRoot, Runnable preLaunch, Runnable postLaunch)
            throws Exception
    {
        if (OSUtil.isOSX()) {
            // Launch AeroFS on startup
            SystemUtil.execBackground(AppRoot.abs().concat("/osxtools"), "loginitem", "rem",
                    "/Applications/" + L.productSpaceFreeName() + ".app");

            SystemUtil.execBackground(AppRoot.abs().concat("/osxtools"), "loginitem", "add",
                    "/Applications/" + L.productSpaceFreeName() + ".app");
        }

        UI.get().preSetupUpdateCheck_();
        UI.get().setup_(rtRoot);
        if (preLaunch != null) { UI.get().asyncExec(preLaunch); }

        if (shouldShowTutorial()) {
            // Show the user tutorial on OS X and Windows
            new DlgTutorial(GUI.get().sh()).openDialog();
        }

        // TODO (WW) don't show this dialog
        if (UI.isGUI() && !L.isMultiuser()) {
            // Check if there are any shared folder invitations to accept
            new DlgJoinSharedFolders(GUI.get().sh()).showDialogIfNeeded();
            // TODO (GS): Needs a similar class for CLI, too
        }

        finishLaunch(postLaunch);

        NotifMessage onClick = null;
        if (!L.isMultiuser()) {
            onClick = new NotifMessage(NotifMessage.LAUNCH, Cfg.absDefaultRootAnchor());
        }

        UI.get().notify(MessageType.INFO, "Up and running. Enjoy!", onClick);
    }

    private static boolean shouldShowTutorial()
    {
        return !L.isMultiuser() && UI.isGUI() && (OSUtil.isOSX() || OSUtil.isWindows());
    }

    private static void logAndShowLaunchError(Throwable e)
    {
        l.warn("", e);

        String errMessage;
        if (e instanceof ExUIMessage) errMessage = e.getMessage();
        else if (e instanceof ExPasswordExpired) errMessage = S.SETUP_SIGNIN_PASSWORD_EXPIRED_ERROR;
        else errMessage = LAUNCH_ERROR_STRING + ": " + ErrorMessages.e2msgSentenceNoBracketDeprecated(e);

        UI.get().show(MessageType.ERROR, errMessage);
    }

    private static void finishLaunch(Runnable postLaunch)
    {
        if (!L.isMultiuser()) {
            // Starts the service that displays notifications when files are updated
            new FileChangeNotification();
        }

        if (postLaunch != null) UI.get().asyncExec(postLaunch);
    }

    /**
     * Prettify a count-prefixed label
     * @param count Number of items associated with the label
     * @param singular Label to use in case the number of items is exactly 1
     * @param plural Label suffix to use in case the numbers of items is different from 1
     *
     * Examples:
     *
     * prettyLabelWithCount(0, "A foo", "bar") -> "No bar"
     * prettyLabelWithCount(1, "A foo", "bar") -> "A foo"
     * prettyLabelWithCount(2, "A foo", "bar") -> "Two bar"
     * prettyLabelWithCount(11, "A foo", "bar") -> "11 bar"
     */
    public static String prettyLabelWithCount(int count, String singular, String plural)
    {
        assert count >= 0;
        final String[] NUMBERS = {"No", "", "Two", "Three", "Four", "Five",
                "Six", "Seven", "Eight", "Nine", "Ten" };
        if (count == 1) return singular;
        return (count < NUMBERS.length ? NUMBERS[count] : count) + " " + plural;
    }

    /**
     * @pre client must use LINKED storage to resolve empty path
     * Derive the name of a shared folder from its Path
     * This is necessary to handle external roots, whose Path are empty and whose name are derived
     * from the physical folder they are linked too.
     */
    public static String sharedFolderName(Path path, String defaultName)
    {
        if (!path.isEmpty()) return path.last();
        String absRootPath = null;
        try {
            absRootPath = Cfg.getRootPathNullable(path.sid());
        } catch (SQLException e) {
            l.error("ignored exception", e);
        }
        return absRootPath != null ? new File(absRootPath).getName() : defaultName;
    }

    /**
     * @pre client must use LINKED storage to resolve empty path
     * Derive the name of a shared folder from its Path
     * This is necessary to handle external roots, whose Path are empty and whose name are derived
     * from the physical folder they are linked too.
     */
    public static String sharedFolderName(Path path, String defaultName, CfgAbsRoots roots)
    {
        if (!path.isEmpty()) return path.last();
        String absRootPath = null;
        try {
            absRootPath = roots.getNullable(path.sid());
        } catch (SQLException e) {
            l.error("ignored exception", e);
        }
        return absRootPath != null ? new File(absRootPath).getName() : defaultName;
    }

    /**
     * Given a collection of PBSharedFolders, gets internal or external folders depending upon
     * the boolean value passed in.
     */
    public static Collection<PBSharedFolder> filterStoresIntoInternalOrExternal(
            Collection<PBSharedFolder> folders, boolean isInternal)
    {
        List<PBSharedFolder> filteredStores = new ArrayList<PBSharedFolder>();

        for (PBSharedFolder folder : folders) {
            // Path.getElemCount is 0 for external folders and > 0 for internal folders.
            if ((folder.getPath().getElemCount() > 0) == isInternal) {
                filteredStores.add(folder);
            }
        }
        // Sort the shared folders based on their names.
        Collections.sort(filteredStores, (o1, o2) -> o1.getName().compareTo(o2.getName()));

        return filteredStores;
    }

    /**
     * @return logical Path corresponding to the given absolute physical path
     *
     * This method takes external roots into account.
     *
     * If the input is not under any of the physical roots, this method returns null
     */
    public static @Nullable Path getPathNullable(String absPath)
    {
        try {
            String canonicalPath = new File(absPath).getCanonicalPath();

            Map<SID, String> roots;
            try {
                roots = Cfg.getRoots();
            } catch (SQLException e) {
                l.error("ignored exception", e);
                roots = Collections.emptyMap();
            }
            for (Entry<SID, String> e : roots.entrySet()) {
                String rootAbsPath = e.getValue();
                if (Path.isUnder(rootAbsPath, canonicalPath)) {
                    return Path.fromAbsoluteString(e.getKey(), rootAbsPath, canonicalPath);
                }
            }
        } catch (IOException e) {
            l.warn("", e);
        }
        return null;
    }

    /**
     * @return absolute path corresponding to a given logical path
     * Will return null if the sid of the path is not associated to any abs path
     */
    public static @Nullable String absPathNullable(Path path)
    {
        String absRoot = null;
        try {
            absRoot = Cfg.getRootPathNullable(path.sid());
        } catch (SQLException e) {
            l.error("ignored exception", e);
        }
        return absRoot != null ? path.toAbsoluteString(absRoot) : null;
    }

    /**
     * @param path - a file path which may or may not contain non-printable characters
     * @return a path based on {@paramref path} where all non-printable characters are replaced
     *   with '?'
     */
    public static @Nonnull String getPrintablePath(@Nonnull String path)
    {
        return path.replaceAll("\\p{C}", "?");
    }
}

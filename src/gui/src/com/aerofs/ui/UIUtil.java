package com.aerofs.ui;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.IExObfuscated;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.controller.ExLaunchAborted;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.setup.DlgJoinSharedFolders;
import com.aerofs.gui.setup.DlgTutorial;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.FullName;
import com.aerofs.lib.JsonFormat.ParseException;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Map.Entry;

public class UIUtil
{
    static final Logger l = Loggers.getLogger(UIUtil.class);

    /**
     * convert an exception to user friendly message
     * @return a string "(<error description>)"
     */
    public static String e2msg(Throwable e)
    {
        return '(' + e2msgNoBracket(e) + ')';
    }

    public static String e2msgSentenceNoBracket(Throwable e)
    {
        String str = e2msgNoBracket(e);

        if (str.isEmpty()) return "";

        str = Character.toUpperCase(str.charAt(0)) + str.substring(1);
        if (!str.endsWith(".")) str += ".";
        return str;
    }

    private static final String SERVER_ERROR = "Server returned HTTP response code: ";

    public static String e2msgNoBracket(Throwable e)
    {
        while (e.getCause() != null) { e = e.getCause(); }

        final String message;
        if (e instanceof IExObfuscated) {
            // Extract the plain text message if this is an obfuscated Exception
            message = ((IExObfuscated) e).getPlainTextMessage();
        } else {
            message = e.getMessage();
        }

        if (e instanceof AbstractExWirable) {
            String wireType = ((AbstractExWirable) e).getWireTypeString();
            return wireType.equals(message) ? wireType : wireType + ": " + message;
        } else if (e instanceof FileNotFoundException) {
            return message + " is not found";
        } else if (e instanceof SocketException) {
            return "connection failed";
        } else if (e instanceof UnknownHostException) {
            return "communication with the server failed";
        } else if (e instanceof ExNoConsole) {
            return "no console for user input";
        } else if (e instanceof EOFException) {
            return "connection failed or end of file";
        } else if (e instanceof ParseException) {
            return "parsing failed";

        // the following tests should go last
        } else if (message == null) {
            return e.getClass().getSimpleName();
        } else if (e instanceof IOException && message.startsWith(SERVER_ERROR)) {
            int start = SERVER_ERROR.length();
            String code = message.substring(start, start + 3);
            return "server error, code " + code;
        } else {
            return message;
        }
    }

    public static boolean isSystemFile(PBPath path)
    {
        for (String elem : path.getElemList()) {
            if (elem.equals(LibParam.TRASH)) return true;
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

    // this is used to sort user lists
    public static int compareUser(UserID u1, UserID u2)
    {
        int comp = u1.compareTo(u2);
        if (comp == 0) return 0;

        UserID me = Cfg.user();
        if (u1.equals(me)) return -1;
        if (u2.equals(me)) return 1;

        return comp;
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
        if (!L.isMultiuser()) {
            SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
            SPBlockingClient sp = fact.create_(Cfg.user());
            sp.signInRemote();
            sp.unlinkDevice(Cfg.did().toPB(), false);
        } else {
            // Currently only the single user unlink is supported.
            // TODO support multi user unlink.
            throw new UnsupportedOperationException();
        }
    }

    private static String getUserFriendlyID(PBSOCID pbsocid)
    {
        ByteString oid = pbsocid.getOid();
        if (L.isStaging()) {
            // for internal use we want the first 3 oid bytes (like git)
            return String.format("%1$02X%2$X", oid.byteAt(0), oid.byteAt(1) & 0xF);
        } else {
            // don't print the 0th and 1st bytes to avoid exposing the actual oid
            return String.format("%1$X%2$02X", oid.byteAt(1) & 0xF, oid.byteAt(2));
        }
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
            " Please contact us at " + WWW.SUPPORT_EMAIL_ADDRESS.get();

    /**
     * Helper method to perform launch for the Java UIs (ie: CLI and GUI)
     * The flow here is:
     *  - CLI or GUI call UIUtil.launch() to perform common launch logic
     *  - UIUtil.launch() call Controller methods
     *  - Controller methods then call Setup or Launcher methods
     *
     *  This logic is (will be) duplicated in the native UIs
     *
     * @param preLaunch a runnable that will be executed in the UI thread, before the launch
     * @param postLaunch a runnable that will be executed in the UI thread, iff the launch succeeds
     */
    public static void launch(String rtRoot, Runnable preLaunch, Runnable postLaunch)
    {
        try {
            GetInitialStatusReply reply = UI.controller().getInitialStatus();
            switch (reply.getStatus()) {

            case NOT_LAUNCHABLE:
                failToLaunch(reply);
                break;

            case NEEDS_SETUP:
                setup(rtRoot, preLaunch, postLaunch);
                break;

            case READY_TO_LAUNCH:
                launch(preLaunch, postLaunch);
                break;
            }
        } catch (ExLaunchAborted e) {
            // User clicked on cancel, exit without error messages
            System.exit(0);
        } catch (Exception e) {
            logAndShowLaunchError(e);
            System.exit(0);
        }
    }

    private static void failToLaunch(GetInitialStatusReply reply)
    {
        UI.get().show(MessageType.ERROR, reply.hasErrorMessage() ? reply.getErrorMessage() :
                LAUNCH_ERROR_STRING + ".");
        System.exit(0);
    }

    private static void launch(Runnable preLaunch, final Runnable postLaunch)
    {
        if (preLaunch != null) { UI.get().asyncExec(preLaunch); }
        Futures.addCallback(UI.controller().async().launch(), new FutureCallback<Common.Void>()
        {
            @Override
            public void onSuccess(Common.Void aVoid)
            {
                finishLaunch(postLaunch);
            }

            @Override
            public void onFailure(Throwable e)
            {
                logAndShowLaunchError(e);
                System.exit(0);
            }
        });
    }

    private static void setup(String rtRoot, Runnable preLaunch, Runnable postLaunch)
            throws Exception
    {
        // TODO: add the same logic in the native Cocoa UI. Currently only setup is run

        if (OSUtil.isOSX()) {
            // Launch AeroFS on startup
            SystemUtil.execBackground(AppRoot.abs().concat("/osxtools"), "loginitem", "rem",
                    AppRoot.abs().replace("Contents/Resources/Java", ""));

            SystemUtil.execBackground(AppRoot.abs().concat("/osxtools"), "loginitem", "add",
                    AppRoot.abs().replace("Contents/Resources/Java", ""));
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

        Runnable onClick = null;

        if (!L.isMultiuser()) {
            onClick = new Runnable()
            {
                @Override
                public void run()
                {
                    GUIUtil.launch(Cfg.absDefaultRootAnchor());
                }
            };
        }

        UI.get().notify(MessageType.INFO, "Up and running. Enjoy!", onClick);
    }

    private static boolean shouldShowTutorial()
    {
        return !L.isMultiuser() && UI.isGUI() && (OSUtil.isOSX() || OSUtil.isWindows());
    }

    private static void logAndShowLaunchError(Throwable e)
    {
        l.warn(Util.e(e));
        UI.get().show(MessageType.ERROR, e instanceof ExUIMessage ? e.getMessage() :
                LAUNCH_ERROR_STRING + ": " + UIUtil.e2msgSentenceNoBracket(e));
    }

    private static void finishLaunch(Runnable postLaunch)
    {
        if (!L.isMultiuser()) {
            // Starts the service that displays notifications when files are updated
            new FileChangeNotification();
        }

        // Start the service that displays Bad Password notifications
        // This should not be run before setup is completed, otherwise it will
        // trigger update password dialogs during setup.
        new RetypePasswordDialogDisplayer();

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
        String absRootPath = Cfg.getRootPath(path.sid());
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
        String absRootPath = roots.get(path.sid());
        return absRootPath != null ? new File(absRootPath).getName() : defaultName;
    }

    /**
     * @return logical Path corresponding to the given absolute physical path
     *
     * This method takes external roots into account.
     *
     * If the input is not under any of the physical roots, this method returns null
     */
    public static @Nullable Path getPath(String absPath)
    {
        try {
            String canonicalPath = new File(absPath).getCanonicalPath();

            Map<SID, String> roots = Cfg.getRoots();
            for (Entry<SID, String> e : roots.entrySet()) {
                String rootAbsPath = e.getValue();
                if (Path.isUnder(rootAbsPath, canonicalPath)) {
                    return Path.fromAbsoluteString(e.getKey(), rootAbsPath, canonicalPath);
                }
            }
        } catch (IOException e) {
            l.warn(Util.e(e));
        }
        return null;
    }

    /**
     * @pre Client must use LINKED storage
     * @return absolute path corresponding to a given logical path
     */
    public static @Nonnull String absPath(Path path)
    {
        return path.toAbsoluteString(Cfg.getRootPath(path.sid()));
    }

    /**
     * @return absolute path corresponding to a given logical path
     * Will return null if the sid of the path is not associated to any abs path
     */
    public static @Nullable String absPathNullable(Path path)
    {
        String absRoot = Cfg.getRootPath(path.sid());
        return absRoot != null ? path.toAbsoluteString(absRoot) : null;
    }
}

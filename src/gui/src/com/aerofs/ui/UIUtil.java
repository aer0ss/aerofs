package com.aerofs.ui;

import com.aerofs.gui.GUI;
import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.setup.DlgJoinSharedFolders;
import com.aerofs.gui.sharing.DlgCreateSharedFolder;
import com.aerofs.gui.sharing.DlgManageSharedFolder;
import com.aerofs.gui.syncstatus.DlgSyncStatus;
import com.aerofs.l.L;
import com.aerofs.lib.*;
import com.aerofs.lib.JsonFormat.ParseException;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.*;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.Sp.ResolveSharedFolderCodeReply;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.IUI.SetupType;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import org.eclipse.swt.program.Program;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.SQLException;

public class UIUtil
{
    static final Logger l = Util.l(UIUtil.class);

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

        if (e instanceof AbstractExWirable) {
            String msg = e.getMessage();
            String wireType = ((AbstractExWirable) e).getWireTypeString();
            return wireType.equals(msg) ? wireType : wireType + ": " + e.getMessage();
        } else if (e instanceof FileNotFoundException) {
            return e.getMessage() + " is not found";
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
        } else if (e.getMessage() == null) {
            return e.getClass().getSimpleName();
        } else if (e instanceof IOException && e.getMessage().startsWith(SERVER_ERROR)) {
            int start = SERVER_ERROR.length();
            String code = e.getMessage().substring(start, start + 3);
            return "server error, code " + code;
        } else {
            return e.getMessage();
        }
    }

    public static boolean isSystemFile(PBPath path)
    {
        for (String elem : path.getElemList()) {
            if (elem.equals(C.TRASH)) return true;
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
     * @param code share folder code
     */
    public static Path joinSharedFolder(SPBlockingClient sp, RitualBlockingClient ritual,
            String code) throws Exception
    {
        sp.signInRemote();
        ResolveSharedFolderCodeReply reply = sp.resolveSharedFolderCode(code);
        Path path = joinSharedFolder(ritual, new SID(reply.getShareId()), reply.getFolderName());
        return path;
    }

    public static Path joinSharedFolder(RitualBlockingClient ritual, SID sid, String folderName)
            throws Exception
    {
        // no code generates empty folder names, but just in case...
        if (folderName.isEmpty()) folderName = S.UNNAMED_SHARED_FOLDER;

        Path path = new Path(folderName);
        // keep trying until finding an unused path
        while (true) {
            try {
                ritual.joinSharedFolder(Cfg.user(), sid.toPB(), path.toPB());
                break;
            } catch (ExAlreadyExist e) {
                path = new Path(Util.newNextFileName(path.last()));
            }
        }

        return path;
    }

    // this is used to sort user lists
    public static int compareUser(String u1, String u2)
    {
        int comp = u1.compareTo(u2);
        if (comp == 0) return 0;

        String me = Cfg.user();
        if (u1.equals(me)) return -1;
        if (u2.equals(me)) return 1;

        if (u1.equals(L.get().spUser())) return 1;
        if (u2.equals(L.get().spUser())) return -1;

        return comp;
    }

    public static void logShowSendDefect(boolean auto, String msg, Throwable e)
    {
        SVClient.logSendDefectAsync(auto, msg, e);
        UI.get().show(MessageType.ERROR, msg);
    }

    /**
     * unlink and exit AeroFS
     */
    public static void unlinkAndExit(InjectableFile.Factory factFile) throws SQLException, IOException
    {
        // delete the password.
        Cfg.db().set(Key.CRED, Key.CRED.defaultValue());
        factFile.create(Util.join(Cfg.absRTRoot(), C.SETTING_UP)).createNewFile();
        UI.get().shutdown();
        System.exit(0);
    }

    private static String getUserFriendlyID(PBSOCID pbsocid)
    {
        ByteString oid = pbsocid.getOid();
        if (Cfg.staging()) {
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
            text = path.toString();
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

    private static String LAUNCH_ERROR_STRING = "Sorry, " + S.PRODUCT + " couldn't launch." +
            " Please contact us at " + SV.SUPPORT_EMAIL_ADDRESS;

    /**
     * Helper method to perform launch for the Java UIs (ie: CLI and GUI)
     * The flow here is:
     *  - CLI or GUI call UIUtil.launch() to perform common launch logic
     *  - UIUtil.launch() call Controller methods
     *  - Controller methods then call Setup or Launcher methods
     *
     *  This logic is (will be) duplicated in the native UIs
     *
     * @param rtRoot
     * @param preLaunch a runnable that will be executed in the UI thread, before the launch
     * @param postLaunch a runnable that will be executed in the UI thread, iff the launch succeeds
     */
    public static void launch(String rtRoot, final Runnable preLaunch, final Runnable postLaunch)
    {
        try {
            GetInitialStatusReply reply = UI.controller().getInitialStatus();
            switch (reply.getStatus()) {

            case NOT_LAUNCHABLE:
                UI.get().show(MessageType.ERROR, reply.hasErrorMessage() ? reply.getErrorMessage() :
                        LAUNCH_ERROR_STRING + ".");
                System.exit(0);
                break; // suppress compiler fall-through warning just for this case

            case NEEDS_SETUP:
                // TODO: add the same logic in the native Cocoa UI. Currently only setup is run

                if (OSUtil.isOSX()) {
                    // Launch AeroFS on startup
                    Util.execBackground(AppRoot.abs().concat("/osxtools"), "loginitem", "rem",
                            AppRoot.abs().replace("Contents/Resources/Java", ""));

                    Util.execBackground(AppRoot.abs().concat("/osxtools"), "loginitem", "add",
                            AppRoot.abs().replace("Contents/Resources/Java", ""));
                }

                UI.get().preSetupUpdateCheck_();
                SetupType st = UI.get().setup_(rtRoot);
                if (preLaunch != null) { UI.get().asyncExec(preLaunch); }
                UI.get().notify(MessageType.INFO, "Up and running. Enjoy!", new Runnable() {
                    @Override
                    public void run()
                    {
                        Program.launch(Cfg.absRootAnchor());
                    }
                });

                if (st == SetupType.NEW_USER && UI.isGUI()) {
                    // Check if there are any shared folder invitations to accept
                    new DlgJoinSharedFolders(GUI.get().sh()).showDialogIfNeeded();
                    // TODO (GS): Needs a similar class for CLI, too
                }

                finishLaunch(postLaunch);
                break;

            case NEEDS_LOGIN:
                UI.get().login_();
                if (preLaunch != null) { UI.get().asyncExec(preLaunch); }
                finishLaunch(postLaunch);
                break;

            case READY_TO_LAUNCH:
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
                break;
            }
        } catch (ExAborted exAb) {
            // User clicked on cancel, exit without error messages
            System.exit(0);
        } catch (Exception e) {
            logAndShowLaunchError(e);
            System.exit(0);
        }
    }

    private static void logAndShowLaunchError(Throwable e)
    {
        l.warn(Util.e(e));
        UI.get().show(MessageType.ERROR, e instanceof ExUIMessage ? e.getMessage() :
                LAUNCH_ERROR_STRING + ": " + UIUtil.e2msgSentenceNoBracket(e));
    }

    private static void finishLaunch(Runnable postLaunch)
    {
        // Starts the service that displays notifications when files are updated
        new FileChangeNotification();
        // Start the service that displays Bad Password notifications
        // This should not be run before setup is completed, otherwise it will
        // trigger update password dialogs during setup.
        new LoginDialogDisplayer();
        if (postLaunch != null) {
            UI.get().asyncExec(postLaunch);
        }
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void createOrManageSharedFolder(final Path path)
    {
        final boolean create;

        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            PBObjectAttributes.Type type = ritual.getObjectAttributes(Cfg.user(), path.toPB())
                    .getObjectAttributes()
                    .getType();
            create = type != PBObjectAttributes.Type.SHARED_FOLDER;
        } catch (Exception e) {
            l.warn(Util.e(e));
            return;
        } finally {
            ritual.close();
        }

        GUI.get().asyncExec(new Runnable() {
            @Override
            public void run()
            {
                if (create) {
                    new DlgCreateSharedFolder(GUI.get().sh(), path).openDialog();
                } else {
                    new DlgManageSharedFolder(GUI.get().sh(), path).openDialog();
                }
            }
        });
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showSyncStatus(final Path path)
    {
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                new DlgSyncStatus(GUI.get().sh(), path).openDialog();
            }
        });
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showVersionHistory(final Path path)
    {
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                new DlgHistory(GUI.get().sh(), path).openDialog();
            }
        });
    }
}

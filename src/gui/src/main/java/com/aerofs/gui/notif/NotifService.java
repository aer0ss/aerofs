package com.aerofs.gui.notif;

import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg.NativeSocketType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.nativesocket.NativeSocketAuthenticatorFactory;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.ritual.RitualClientProvider;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import org.slf4j.Logger;

public class NotifService
{
    private static final Logger l = Loggers.getLogger(NotifService.class);

    private final RitualClientProvider _ritualProvider;
    private NotifServer _server;

    public NotifService(RitualClientProvider ritualProvider)
    {
        _ritualProvider = ritualProvider;
    }

    public void start_()
    {
        if (_server == null) {
            File socketFile = new File(Cfg.nativeSocketFilePath(NativeSocketType.NOTIF));
            if (!socketFile.exists()) {
                socketFile.getParentFile().mkdirs();
            }

            _server = new NotifServer(this, socketFile,
                    NativeSocketAuthenticatorFactory.create());
            _server.start_();
        }
    }

    protected void react(String message) throws ExProtocolError
    {
        int dividerIdx = message.indexOf(":");
        if (!execNotifFunc(message.substring(0, dividerIdx),
                           message.substring(dividerIdx + 1))) {
            throw new ExProtocolError(message);
        }
    }

    private static String normalize(String path)
    {
        return OSUtil.get().normalizeInputFilename(path);
    }

    public static boolean execNotifFunc(String func, String arg) {
        switch (func) {
        case NotifMessage.INSTALL_SHELLEXT:
            installShellext();
            return true;
        case NotifMessage.LAUNCH:
            launchPath(normalize(arg));
            return true;
        case NotifMessage.OPEN:
            openPath(normalize(arg));
            return true;
        default:
            return false;
        }
    }

    private static void installShellext()
    {
        UI.get().asyncExec(() -> {
            try {
                OSUtil.get().installShellExtension(false);
            } catch (Exception e) {
                GUI.get().reportShellExtInstallFailed(e);
            }
        });
    }

    private static void launchPath(final String absPath)
    {
        GUIUtil.launch(absPath);
    }

    private static void openPath(final String absPath)
    {
        OSUtil.get().showInFolder(absPath);
    }
}

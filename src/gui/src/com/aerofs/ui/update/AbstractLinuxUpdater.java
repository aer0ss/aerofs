package com.aerofs.ui.update;

import java.io.File;
import java.io.IOException;

import com.aerofs.labeling.L;
import com.aerofs.lib.*;

import com.aerofs.lib.Param.SP;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

abstract class AbstractLinuxUpdater extends Updater
{
    private static final InjectableFile.Factory s_factFile = new InjectableFile.Factory();

    AbstractLinuxUpdater(String installerFilenameFormat)
    {
        super(installerFilenameFormat);
    }

    protected final void execUpdateCommon(String instfile, String newVer, boolean hasPermissions)
    {
        try {
            execUpdateCommonImpl(instfile, newVer, hasPermissions);
        } catch (IOException e) {
            l.warn("update: " + e);
        }
    }

    private void execUpdateCommonImpl(String instfile, String newVer, boolean hasPermissions)
            throws IOException
    {
        final String appRoot = new File(AppRoot.abs()).getAbsolutePath();
        InjectableFile upFile = s_factFile.createTempFile(L.get().productUnixName() +
                                                  "update" + newVer, "$$$");

        s_factFile.create(Util.join(appRoot, "updater.sh")).copy(upFile, false, false);

        if (hasPermissions) {
            // the updater has permissions
            String userId = System.getenv("USER");
            if (userId == null) userId = "null";

            SystemUtil.execBackground("/bin/bash", upFile.getAbsolutePath(),
                    appRoot + File.separator, Util.join(Cfg.absRTRoot(), C.UPDATE_DIR, instfile),
                    newVer, userId,
                    //need to pass in username
                    UI.isGUI() ? "1" : "0" // run GUI on startup? or cli?
            );

            UI.get().shutdown();
            System.exit(0);

        } else {
            // the updater has no permissions to write approot
            String text = "An Update has been downloaded" +
                    " for your " + L.PRODUCT + ", but you do not have sufficient" +
                    " permission to apply it. Please *recursively* grant" +
                    " yourself write permission to " + appRoot + " at your" +
                    " earliest convenience, and " + L.PRODUCT + " will" +
                    " try again later. Files might stop syncing without the update.";

            try {
                UI.get().confirm(MessageType.ERROR, text);
            } catch (ExNoConsole e) {
                UI.get().show(MessageType.INFO,
                        "Could not confirm with the" + " user. Send an email instead.");

                SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
                try {
                    sp.signInRemote();

                    String deviceName = sp.getPreferences(Cfg.did().toPB()).getDeviceName();

                    final String subject = "[Action Required] Update " + L.PRODUCT +
                                " on " + Util.quote(deviceName);

                    final String body = "Hello,\n\n" + text +
                                        "\n\n" +
                                        "Best,\n" +
                                        "The " + L.PRODUCT +
                                        " Support Team\n\n" +
                                        "P.S. This is an auto-generated email by your own " +
                                        L.PRODUCT + " Client :)";
                    sp.emailUser(subject, body);
                } catch (Exception e2) {
                    l.warn("can't send email. ignored: " + Util.e(e));
                }
            }
        }
    }
}

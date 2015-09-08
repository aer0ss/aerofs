package com.aerofs.ui.update;

import java.io.IOException;

import com.aerofs.base.BaseUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

abstract class AbstractLinuxUpdater extends Updater
{
    private static final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    AbstractLinuxUpdater(String installerFilenameFormat)
    {
        super(installerFilenameFormat);
    }

    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version {}", newVersion);

        l.debug("rtRoot is {}", Cfg.absRTRoot());
        InjectableFile appRoot = _factFile.create(AppRoot.abs());
        l.debug("appRoot is {}", appRoot.getAbsolutePath());

        try {
            InjectableFile upFile = _factFile.createTempFile(L.productUnixName() +
                                                      "update" + newVersion, "$$$");

            _factFile.create(appRoot, "updater.sh").copy(upFile, false, false);

            if (hasPermissions) {
                String userId = System.getenv("USER");
                if (userId == null) userId = "";

                SystemUtil.execBackground("/bin/bash", upFile.getAbsolutePath(),
                        appRoot.getAbsolutePath(),
                        Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR, installerFilename),
                        newVersion,
                        userId,
                        UI.isGUI() ? "1" : "0");

                UI.get().shutdown();
            } else {
                String text = "An Update has been downloaded for " + L.product() +
                        ", but you do not have sufficient permission to apply it." +
                        " Please *recursively* grant yourself write permission to " + appRoot +
                        " at your earliest convenience, and " + L.product() + " will" +
                        " try again later. Files might stop syncing without the update.";

                try {
                    UI.get().confirm(MessageType.ERROR, text);
                } catch (ExNoConsole e) {
                    UI.get().show(MessageType.INFO,
                            "Could not confirm with the user. Send an email instead.");

                    try {
                        SPBlockingClient sp = newMutualAuthClientFactory().create()
                                .signInRemote();
                        String deviceName = sp.getUserPreferences(BaseUtil.toPB(Cfg.did())).getDeviceName();

                        final String subject = "[Action Required] Update " + L.product() +
                                    " on " + Util.quote(deviceName);

                        final String body = "Hello,\n\n" + text +
                                            "\n\n" +
                                            "Best,\n" +
                                            "The " + L.product() +
                                            " Support Team\n\n" +
                                            "P.S. This is an auto-generated email by your own " +
                                            L.product() + " Client :)";
                        sp.emailUser(subject, body);
                    } catch (Exception e2) {
                        l.warn("can't send email. ignored: ", e);
                    }
                }
            }
        } catch (IOException e) {
            l.warn("update: " + e);
        }
    }
}

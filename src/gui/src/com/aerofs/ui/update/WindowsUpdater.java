package com.aerofs.ui.update;

import java.io.IOException;

import com.aerofs.labeling.L;
import com.aerofs.lib.C;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

class WindowsUpdater extends Updater
{
    WindowsUpdater()
    {
        super(L.get().productSpaceFreeName() + "Install-%s.exe");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version " + newVersion);

        if (!hasPermissions) {
            SVClient.logSendDefectAsync(true, "no perm 2 update on win?");
            UI.get().show(MessageType.ERROR, "You have no permissions to apply" + " the update.");

        } else {
            try {
                SystemUtil.execBackground(
                        Util.join(Cfg.absRTRoot(), C.UPDATE_DIR, installerFilename), "/S");

                UI.get().shutdown();
                System.exit(0);

            } catch (IOException e) {
                l.warn("update: " + e);
            }
        }
    }
}

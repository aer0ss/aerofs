package com.aerofs.ui.update;

import java.io.IOException;

import com.aerofs.labeling.L;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

import static com.aerofs.defects.Defects.newDefectWithLogs;

class WindowsUpdater extends Updater
{
    WindowsUpdater()
    {
        super(L.productSpaceFreeName() + "Install-%s.exe");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version " + newVersion);

        if (!hasPermissions) {
            newDefectWithLogs("updater.update.windows")
                    .setMessage("no perm to update on win?")
                    .sendAsync();
            UI.get().show(MessageType.ERROR, "You have no permissions to apply" + " the update.");

        } else {
            try {
                SystemUtil.execBackground(
                        Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR, installerFilename), "/S");

                UI.get().shutdown();

            } catch (IOException e) {
                l.warn("update: " + e);
            }
        }
    }
}

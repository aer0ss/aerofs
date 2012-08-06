package com.aerofs.ui.update;

import com.aerofs.lib.Util;

class Linux32Updater extends AbstractLinuxUpdater
{
    Linux32Updater()
    {
        super("aerofs-%s-x86.tgz", null);
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        Util.l(this).info("update to version " + newVersion);
        execUpdateCommon(installerFilename, newVersion, hasPermissions);
    }
}

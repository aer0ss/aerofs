package com.aerofs.ui.update;

import com.aerofs.labeling.L;
import com.aerofs.lib.Util;

class Linux32Updater extends AbstractLinuxUpdater
{
    Linux32Updater()
    {
        super(L.get().productUnixName() + "-%s-x86.tgz");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        Util.l(this).info("update to version " + newVersion);
        execUpdateCommon(installerFilename, newVersion, hasPermissions);
    }
}

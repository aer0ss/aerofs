package com.aerofs.ui.update;

import com.aerofs.labeling.L;

class Linux32Updater extends AbstractLinuxUpdater
{
    Linux32Updater()
    {
        super(L.productUnixName() + "-%s-x86.tgz");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version " + newVersion);
        execUpdateCommon(installerFilename, newVersion, hasPermissions);
    }
}

package com.aerofs.ui.update;

import com.aerofs.labeling.L;

class Linux64Updater extends AbstractLinuxUpdater
{
    Linux64Updater()
    {
        super(L.get().productUnixName() + "-%s-x86_64.tgz");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version " + newVersion);
        execUpdateCommon(installerFilename, newVersion, hasPermissions);
    }
}
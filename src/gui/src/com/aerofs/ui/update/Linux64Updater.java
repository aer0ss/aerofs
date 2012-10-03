package com.aerofs.ui.update;

class Linux64Updater extends AbstractLinuxUpdater
{
    Linux64Updater()
    {
        super("aerofs-%s-x86_64.tgz");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version " + newVersion);
        execUpdateCommon(installerFilename, newVersion, hasPermissions);
    }
}
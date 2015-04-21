package com.aerofs.ui.update;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import java.io.IOException;

public class DisabledUpdater extends Updater {

    private static final Logger l = Loggers.getLogger(DisabledUpdater.class);

    protected DisabledUpdater()
    {
        super("disabled");
    }

    @Override
    public void onStartup()
    {
        // no-op
    }

    @Override
    public void onStartupFailed()
    {
        // no-op
    }

    @Override
    public void start()
    {
        // no-op
    }

    @Override
    public void forceCanaryUntilRelaunch()
    {
        throw new UnsupportedOperationException("Disabled updater does not force canary.");
    }

    @Override
    public void checkForUpdate(boolean newThread)
    {
        throw new UnsupportedOperationException("Disabled updater does not check for update.");
    }

    @Override
    public void execUpdateFromMenu()
    {
        throw new UnsupportedOperationException("Disabled updater does not exec update from menu.");
    }

    @Override
    protected void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        throw new UnsupportedOperationException("Disabled updater does not update.");
    }
}

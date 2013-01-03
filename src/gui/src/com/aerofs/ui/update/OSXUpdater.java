package com.aerofs.ui.update;

import java.io.IOException;

import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

class OSXUpdater extends Updater
{
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    OSXUpdater()
    {
        super(L.get().productUnixName() + "-osx-%s.zip");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        Util.l(this).info("update to version " + newVersion);

        InjectableFile appRoot = _factFile.create(AppRoot.abs());

        // Go 3 levels up app root to get the package root.
        InjectableFile packageRoot;
        assert appRoot.getName().equals("Java");
        packageRoot = appRoot.getParentFile();
        assert packageRoot.getName().equals("Resources");
        packageRoot = packageRoot.getParentFile();
        assert packageRoot.getName().equals("Contents");
        packageRoot = packageRoot.getParentFile();

        try {
            InjectableFile upFile = _factFile.createTempFile(L.get().productUnixName() +
                                                     "update" + newVersion, "$$$");

            _factFile.create(appRoot, "updater.sh").copy(upFile, false, false);

            /*
            * On OSX, when you first copy the application into /Applications,
            * the OS will set the ownership of the /Applications/*.app folder
            * to the user who is copying. If the user is not an administrator, an
            * Authorization window will pop up but the actual user doing the copying (e.g. not
            * the admin user) will still have ownership.
            *
            * The Authorization API on OSX does not have a username associated with it, only admin
            * priv (e.g. you can execute code with root priviliges, but not as the root user)
            */

            if (hasPermissions) {
                SystemUtil.execBackground("/bin/bash", upFile.getAbsolutePath(),
                        packageRoot.getAbsolutePath(),
                        Util.join(Cfg.absRTRoot(), "update", installerFilename), newVersion,
                        System.getenv("USER"));

                UI.get().shutdown();
                System.exit(0);
            } else {
                //the update must be executed as the user who originally copied AeroFS into
                // the /Applications folder
                OutArg<String> username = new OutArg<String>();
                SystemUtil.execForeground(username, "stat", "-f", "%Su",
                        packageRoot.getAbsolutePath());

                UI.get().show(MessageType.WARN, L.PRODUCT +
                                                " updates can only be applied from the account which" +
                                                " first installed " + L.PRODUCT + " (\"" +
                                                username.get().trim() +
                                                "\"). Please switch to that" +
                                                " account and check for updates by going to " +
                                                L.PRODUCT + " Help -> About " + L.PRODUCT + " ->" +
                                                " Update Now.");
            }
        } catch (IOException e) {
            Util.l(this).warn("update: " + e);
        }
    }
}

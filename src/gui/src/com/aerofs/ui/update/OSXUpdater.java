package com.aerofs.ui.update;

import java.io.IOException;

import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ClientParam;
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
        super(L.productUnixName() + "-osx-%s.zip");
    }

    @Override
    public void update(String installerFilename, String newVersion, boolean hasPermissions)
    {
        l.info("update to version {}", newVersion);

        // detect and support being run outside of /Applications (e.g. ~/Applications)
        // NB: the golang updater which chainloads the C launcher passes the launcher path as an
        // env var. Walk upwards out of Content/MacOS to the root of the app bundle
        String appname = L.productSpaceFreeName() + ".app";
        String pkg = System.getenv("AERO_APP_PATH");
        if (!pkg.isEmpty()) {
            pkg = _factFile.create(pkg).getParentFile().getParent();
        }
        l.info("package path from env {}", pkg);
        // fallback to default system-wide install path if the env var is missing or invalid
        if (!pkg.endsWith("/" + appname)) {
            pkg = "/Applications/" + appname;
            l.info("fallback package path {}", pkg);
        }

        String appRoot = AppRoot.abs();
        l.debug("appRoot is {}", appRoot);

        try {
            InjectableFile upFile = _factFile.createTempFile(L.productUnixName() +
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
            * priv (e.g. you can execute code with root privileges, but not as the root user)
            */

            if (hasPermissions) {
                SystemUtil.execBackground("/bin/bash", upFile.getAbsolutePath(),
                        pkg,
                        appRoot,
                        Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR, installerFilename),
                        newVersion,
                        System.getenv("USER"));

                UI.get().shutdown();
            } else {
                // the update must be executed as the user who originally
                // copied AeroFS into the /Applications folder
                OutArg<String> username = new OutArg<String>();
                SystemUtil.execForeground(username, "stat", "-f", "%Su", pkg);

                UI.get().show(MessageType.WARN, L.product() +
                                                " updates can only be applied from the account which" +
                                                " first installed " + L.product() + " (\"" +
                                                username.get().trim() +
                                                "\"). Please switch to that" +
                                                " account and check for updates by going to " +
                                                L.product() + " Help -> About " + L.product() + " ->" +
                                                " Update Now.");
            }
        } catch (IOException e) {
            l.warn("update: " + e);
        }
    }
}

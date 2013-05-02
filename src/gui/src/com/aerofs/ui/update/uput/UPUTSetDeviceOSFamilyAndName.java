/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.update.uput;

import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import org.slf4j.Logger;

public class UPUTSetDeviceOSFamilyAndName implements IUIPostUpdateTask
{
    private static final Logger l = Loggers.getLogger(UPUTSetDeviceOSFamilyAndName.class);
    @Override
    public void run()
            throws Exception
    {
        try {
            SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
            SPBlockingClient sp = fact.create_(Cfg.user());
            sp.signInRemote();
            IOSUtil osu = OSUtil.get();
            sp.setDeviceOSFamilyAndName(Cfg.did().toPB(), osu.getOSFamily().getString(),
                    osu.getFullOSName());
        } catch (Throwable e) {
            l.warn("Failed to set Device OS Family and Name");
            UI.rockLog().newDefect("ui.set_device_info").setException(e).send();
        }
    }
}

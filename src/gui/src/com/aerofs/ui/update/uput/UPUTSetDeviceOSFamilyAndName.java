/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.update.uput;

import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UIGlobals;
import org.slf4j.Logger;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class UPUTSetDeviceOSFamilyAndName implements IUIPostUpdateTask
{
    private static final Logger l = Loggers.getLogger(UPUTSetDeviceOSFamilyAndName.class);
    @Override
    public void run()
            throws Exception
    {
        try {
            IOSUtil osu = OSUtil.get();
            newMutualAuthClientFactory().create()
                    .signInRemote()
                    .setDeviceOSFamilyAndName(Cfg.did().toPB(),
                            osu.getOSFamily().getString(), osu.getFullOSName());
        } catch (Throwable e) {
            l.warn("Failed to set Device OS Family and Name");
            UIGlobals.rockLog().newDefect("ui.set_device_info").setException(e).send();
        }
    }
}

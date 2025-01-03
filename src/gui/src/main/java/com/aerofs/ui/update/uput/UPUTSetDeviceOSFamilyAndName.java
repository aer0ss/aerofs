/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.update.uput;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.defects.Defects;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
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
                    .setDeviceOSFamilyAndName(BaseUtil.toPB(Cfg.did()),
                            osu.getOSFamily().getString(), osu.getFullOSName());
        } catch (Throwable e) {
            l.warn("Failed to set Device OS Family and Name");
            Defects.newMetric("ui.set_device_info")
                    .setException(e)
                    .sendAsync();
        }
    }
}

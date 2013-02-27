/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.update.uput;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;

public class UPUTSetDeviceOSFamilyAndName implements IUIPostUpdateTask
{
    @Override
    public void run()
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.signInRemote();
        IOSUtil osu = OSUtil.get();
        sp.setDeviceOSFamilyAndName(Cfg.did().toPB(), osu.getOSFamily().getString(),
                osu.getFullOSName());
    }

    @Override
    public boolean isRebootSuggested()
    {
        return false;
    }

    @Override
    public boolean isShutdownRequired()
    {
        return false;
    }

    @Override
    public String[] getNotes()
    {
        return null;
    }
}

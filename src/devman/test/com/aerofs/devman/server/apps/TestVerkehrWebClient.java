/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.apps;

import com.aerofs.devman.server.VerkehrWebClient;
import com.aerofs.devman.server.VerkehrWebClient.OnlineDeviceInfo;

import java.util.Collection;

/**
 * This class is used for ad-hoc testing and can be deleted at at some point in the future.
 */
public class TestVerkehrWebClient
{
    public static void main(String[] args)
            throws Exception
    {
        VerkehrWebClient vkclient =
                new VerkehrWebClient("verkehr.aerofs.com", (short) 9019);

        Collection<OnlineDeviceInfo> onlineDevicesInfo = vkclient.getOnlineDevicesInfo();

        for (OnlineDeviceInfo onlineDeviceInfo : onlineDevicesInfo) {
            System.out.println(
                    onlineDeviceInfo.getDevice().toStringFormal() + " " +
                    onlineDeviceInfo.getAddress().getHostAddress());
        }
    }
}
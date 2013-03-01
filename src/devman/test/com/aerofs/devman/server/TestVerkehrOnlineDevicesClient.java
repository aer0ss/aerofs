package com.aerofs.devman.server;

import com.aerofs.base.id.DID;

import java.util.Collection;

/**
 * This class is used for ad-hoc testing and can be deleted at at some point in the future.
 */
public class TestVerkehrOnlineDevicesClient
{
    public static void main(String[] args)
            throws Exception
    {
        VerkehrOnlineDevicesClient vkclient =
                new VerkehrOnlineDevicesClient("verkehr.aerofs.com", (short) 9019);

        Collection<DID> onlineDevices = vkclient.getOnlineDevices();

        for (DID did : onlineDevices) {
            System.out.println(did.toStringFormal());
        }
    }
}
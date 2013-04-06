/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.devman.server.VerkehrWebClient.OnlineDeviceInfo;
import com.aerofs.devman.server.db.IPAddressDatabase;
import com.aerofs.devman.server.db.LastSeenDatabase;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.amazonaws.util.json.JSONException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collection;

/**
 * The verkehr puller is responsible for pulling the following information from the verkehr admin
 * interface for a given device:
 *
 *   1. The time the device was last seen online (the "last seen time").
 *   2. The last known IP address for that device.
 */
public class VerkehrPuller implements Runnable
{
    private static final Logger l = Loggers.getLogger(VerkehrPuller.class);

    private final VerkehrWebClient _vkclient;

    private final JedisThreadLocalTransaction _trans;
    private final LastSeenDatabase _lsdb;
    private final IPAddressDatabase _ipdb;

    public VerkehrPuller(VerkehrWebClient vkclient,
            JedisThreadLocalTransaction trans)
    {
        _vkclient = vkclient;
        _trans = trans;
        _lsdb = new LastSeenDatabase(_trans);
        _ipdb = new IPAddressDatabase(_trans);
    }

    @Override
    public void run()
    {
         try {
             l.debug("verkehr pull: start");
             updateDatabaseUsingVerkehr();
             l.debug("verkehr pull: done");
         } catch(Exception e) {
             l.error("puller error: " + e);
             _trans.cleanUp();
         }
    }

    private void updateDatabaseUsingVerkehr()
            throws ExFormatError, IOException, JSONException
    {
        Collection<OnlineDeviceInfo> onlineDevicesInfo = _vkclient.getOnlineDevicesInfo();

        _trans.begin();
        for (OnlineDeviceInfo onlineDeviceInfo : onlineDevicesInfo) {
            l.debug("online: " + onlineDeviceInfo.getDevice().toStringFormal());

            _lsdb.setDeviceSeenNow(onlineDeviceInfo.getDevice());
            _ipdb.setIPAddress(onlineDeviceInfo.getDevice(), onlineDeviceInfo.getAddress());
        }
        _trans.commit();
    }
}
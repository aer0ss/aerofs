/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.devman.server.db.LastSeenDatabase;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.amazonaws.util.json.JSONException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collection;

public class LastSeenTimePuller implements Runnable
{
    private static final Logger l = Loggers.getLogger(LastSeenTimePuller.class);

    private final VerkehrOnlineDevicesClient _vkclient;

    private final JedisThreadLocalTransaction _trans;
    private final LastSeenDatabase _db;

    public LastSeenTimePuller(VerkehrOnlineDevicesClient vkclient,
            JedisThreadLocalTransaction trans)
    {
        _vkclient = vkclient;
        _trans = trans;
        _db = new LastSeenDatabase(_trans);
    }

    @Override
    public void run()
    {
         try {
             l.debug("verkehr pull: start");
             updateLastSeenTimeDatabaseUsingVerkehr();
             l.debug("verkehr pull: done");
         } catch(Exception e) {
             l.error("puller error: " + Util.e(e));
             _trans.cleanUp();
         }
    }

    private void updateLastSeenTimeDatabaseUsingVerkehr()
            throws ExFormatError, IOException, JSONException
    {
        Collection<DID> onlineDevices = _vkclient.getOnlineDevices();

        _trans.begin();
        for (DID device : onlineDevices) {
            l.debug("online: " + device.toStringFormal());
            _db.setDeviceSeenNow(device);
        }
        _trans.commit();
    }
}
/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.resources;

import com.aerofs.devman.server.api.Device;
import com.aerofs.devman.server.ResourceConstants;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.devman.server.db.IPAddressDatabase;
import com.aerofs.devman.server.db.IPAddressDatabase.IPAddress;
import com.aerofs.devman.server.db.LastSeenDatabase;
import com.aerofs.devman.server.db.LastSeenDatabase.LastSeenTime;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import org.slf4j.Logger;

import java.net.UnknownHostException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/" + ResourceConstants.DEVICES_PATH)
public final class DeviceResource
{
    private static final Logger l = Loggers.getLogger(DeviceResource.class);

    private final DID _did;
    private final JedisThreadLocalTransaction _trans;
    private final LastSeenDatabase _lsdb;
    private final IPAddressDatabase _ipdb;

    public DeviceResource(DID did, JedisThreadLocalTransaction trans)
    {
        _did = did;
        _trans = trans;
        _lsdb = new LastSeenDatabase(_trans);
        _ipdb = new IPAddressDatabase(_trans);
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Device getDevice()
            throws ExNotFound, UnknownHostException
    {
        l.info("GET did=" + _did.toStringFormal());

        _trans.begin();
        LastSeenTime lst = _lsdb.getLastSeenTime(_did);
        IPAddress addr = _ipdb.getIPAddress(_did);
        _trans.commit();

        if (!lst.exists() || !addr.exists()) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        return new Device(lst.get(), addr.get());
    }
}
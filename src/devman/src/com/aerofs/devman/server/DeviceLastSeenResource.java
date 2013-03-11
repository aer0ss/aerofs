/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.devman.server.db.LastSeenDatabase;
import com.aerofs.devman.server.db.LastSeenDatabase.LastSeenTime;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import org.slf4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/" + ResourceConstants.LAST_SEEN_PATH)
public final class DeviceLastSeenResource
{
    private static final Logger l = Loggers.getLogger(DeviceLastSeenResource.class);

    private final DID _did;
    private final JedisThreadLocalTransaction _trans;
    private final LastSeenDatabase _db;

    public DeviceLastSeenResource(DID did, JedisThreadLocalTransaction trans)
    {
        _did = did;
        _trans = trans;
        _db = new LastSeenDatabase(_trans);
    }

    @GET
    @Produces(APPLICATION_JSON)
    public long getLastSeenTime()
            throws ExNotFound
    {
        l.info("last_seen did=" + _did.toStringFormal());

        _trans.begin();
        LastSeenTime lst = _db.getLastSeenTime(_did);
        _trans.commit();

        if (!lst.exists()) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        return lst.get();
    }
}
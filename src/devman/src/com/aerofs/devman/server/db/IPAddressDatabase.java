/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.db;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.AbstractJedisDatabase;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import org.slf4j.Logger;
import redis.clients.jedis.Response;

import java.net.UnknownHostException;
import java.net.InetAddress;

public class IPAddressDatabase extends AbstractJedisDatabase
{
    private static final Logger l = Loggers.getLogger(IPAddressDatabase.class);

    public IPAddressDatabase(JedisThreadLocalTransaction transaction)
    {
        super(transaction);
    }

    //
    // Key Generators
    //

    // Device IP (not using "ip/" because we have a 3 character followed by slash convention.
    private static final String KEY_PREFIX = "dip/";
    private static String getKey(DID did)
    {
        return KEY_PREFIX + did.toStringFormal();
    }

    //
    // Public Methods
    //

    public void setIPAddress(DID did, InetAddress address)
    {
        String addressString = address.getHostAddress();
        l.debug("set ip=" + addressString);

        getTransaction().set(getKey(did), addressString);
    }

    public IPAddress getIPAddress(DID did)
    {
        Response<String> response = getTransaction().get(getKey(did));
        return new IPAddress(response);
    }

    //
    // Wrapper Classes
    //

    public static class IPAddress
    {
        private final Response<String> _response;

        public IPAddress(Response<String> response)
        {
            _response = response;
        }

        public boolean exists()
        {
            return _response.get() != null;
        }

        public InetAddress get()
                throws ExNotFound, UnknownHostException
        {
            if (!exists()) {
                throw new ExNotFound();
            }

            l.debug("get ip={}", _response.get());
            return InetAddress.getByName(_response.get());
        }
    }
}
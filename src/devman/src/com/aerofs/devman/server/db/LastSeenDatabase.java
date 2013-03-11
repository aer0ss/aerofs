package com.aerofs.devman.server.db;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.AbstractJedisDatabase;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import redis.clients.jedis.Response;

public class LastSeenDatabase extends AbstractJedisDatabase
{
    public LastSeenDatabase(JedisThreadLocalTransaction transaction)
    {
        super(transaction);
    }

    //
    // Key Generators
    //

    private static final String KEY_PREFIX = "dev/";
    private static String getKey(DID did)
    {
        return KEY_PREFIX + did.toStringFormal();
    }

    //
    // Public Methods
    //

    public void setDeviceSeenNow(DID did)
    {
        String currentTime = String.valueOf(System.currentTimeMillis());
        getTransaction().set(getKey(did), currentTime);
    }

    public LastSeenTime getLastSeenTime(DID did)
    {
        Response<String> response = getTransaction().get(getKey(did));
        return new LastSeenTime(response);
    }

    //
    // Wrapper Classes
    //

    public static class LastSeenTime
    {
        private final Response<String> _response;

        public LastSeenTime(Response<String> response)
        {
            _response = response;
        }

        public boolean exists()
        {
            return _response.get() != null;
        }

        public long get()
                throws ExNotFound
        {
            if (!exists()) {
                throw new ExNotFound();
            }

            return Long.valueOf(_response.get());
        }
    }
}
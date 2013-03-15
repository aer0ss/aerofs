/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.apps;

import com.aerofs.devman.server.VerkehrWebClient;
import com.aerofs.devman.server.VerkehrPuller;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;

/**
 * This class is used for ad-hoc testing and can be deleted at at some point in the future.
 */
public class TestVerkehrPuller
{
    public static void main(String[] args)
            throws Exception
    {
        PooledJedisConnectionProvider provider = new PooledJedisConnectionProvider();
        JedisThreadLocalTransaction trans = new JedisThreadLocalTransaction(provider);
        provider.init_("localhost", (short) 6379);

        VerkehrWebClient vkclient =
                new VerkehrWebClient("verkehr.aerofs.com", (short) 9019);

        VerkehrPuller puller = new VerkehrPuller(vkclient, trans);
        puller.run();
    }
}
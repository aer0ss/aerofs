/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.jedis;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;

public class JEQEnqueueUnlink
{
    private static final PooledJedisConnectionProvider provider =
            new PooledJedisConnectionProvider();
    private static final JedisThreadLocalTransaction trans =
            new JedisThreadLocalTransaction(provider);
    private static final JedisEpochCommandQueue queue = new JedisEpochCommandQueue(trans);

    public static void main(String[] args)
            throws ExFormatError
    {
        provider.init_("localhost", (short) 6379);

        if (args.length != 1)
        {
            System.out.println("Usage: jeq-enqueue-unlink <did>");
            System.exit(1);
        }

        DID did = new DID(args[0]);

        trans.begin();
        queue.enqueue(did, CommandType.UNLINK_AND_WIPE_SELF);
        trans.commit();

        System.out.println("Success.");
    }
}
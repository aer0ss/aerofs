/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.servlets.lib.db.jedis.AbstractJedisDatabase;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import redis.clients.jedis.Response;

public class JedisRateLimiter extends AbstractJedisDatabase
{
    private static final Logger l = Loggers.getLogger(JedisRateLimiter.class);
    private final JedisThreadLocalTransaction _trans;
    private final int _maxBurst;
    private final long _window;
    private final String _prefix;

    /**
     * Construct a rate limiter which allows "maxBurst" requests every "window" milliseconds
     *
     * @param trans Jedis transaction object
     * @param maxBurst number of conformant requests allowed per "window" milliseconds
     * @param window time window in milliseconds
     * @param prefix unique string per rate limit category, used to prefix redis keys
     */
    public JedisRateLimiter(JedisThreadLocalTransaction trans, int maxBurst, long window,
            String prefix)
    {
        super(trans);
        Preconditions.checkArgument(maxBurst > 0);
        Preconditions.checkArgument(window > 0);
        _trans = trans;
        _maxBurst = maxBurst;
        _window = window;
        _prefix = prefix;
    }

    /**
     * Call this when a request identified by "keys" was made
     *
     * @return true if the request was non-conformant
     */
    public boolean update(String... keys)
    {
        long currentTime = System.currentTimeMillis();
        return updateAtTime(currentTime, keys);
    }


    /**
     * tl;dr don't use this method, use update()
     *
     * Split the implementation of update() into a separate method with currentTime explicitly
     * passed in, so that this method can be easily unit tested without mocking
     * System.currentTimeMillis(). Real callers should use update() instead.
     */
    boolean updateAtTime(long currentTime, String... keys)
    {
        String joinedKeys = Joiner.on(":").join(keys);
        String redisKey = Joiner.on(":").join(_prefix, joinedKeys);
        String redisCurrentTime = Long.toString(currentTime);
        String redisBurst = Integer.toString(_maxBurst);
        String redisWindow = Long.toString(_window);

        _trans.begin();
        Response<Object> r = getTransaction().eval(getRateLimitLuaScript(), 1, redisKey,
                // redis eval keys
                redisCurrentTime, redisBurst, redisWindow); // redis eval args
        _trans.commit();

        // response is 1 if the request was non-conformant
        long response = (Long)r.get();
        if (response == 0) l.info("rate limiter for {} returned {}", redisKey, response);
        else l.warn("rate limiter for {} returned {}", redisKey, response);

        return response == 1;
    }

    private static String getRateLimitLuaScript()
    {
        /**
         * KEYS[1] = suggested: "rl:<ip>:<resource_id>"
         *
         * ARGV[1] = current time in millis
         * ARGV[2] = burst size
         * ARGV[3] = window size in millis
         *
         * returns 1 if the request is non-conformant, 0 otherwise
         */
        return "if tonumber(redis.call('LLEN', KEYS[1])) < tonumber(ARGV[2]) then\n" +
               "    redis.call('RPUSH', KEYS[1], ARGV[1])\n" +
               "    return 0\n" +
               "end\n" +
               "\n" +
               "local oldest = redis.call('LPOP', KEYS[1])\n" +
               "if tonumber(oldest) > ARGV[1] - ARGV[3] then\n" +
               "    redis.call('LPUSH', KEYS[1], oldest)\n" +
               "    return 1\n" +
               "end\n" +
               "\n" +
               "redis.call('RPUSH', KEYS[1], ARGV[1])\n" +
               "return 0";
    }
}

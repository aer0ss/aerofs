package com.aerofs.devman.server.config;

import com.aerofs.lib.LibParam.REDIS;

public class RedisConfiguration
{
    public String getHost() {
        return REDIS.ADDRESS.getHostName();
    }

    public int getPort() {
        return REDIS.ADDRESS.getPort();
    }
}

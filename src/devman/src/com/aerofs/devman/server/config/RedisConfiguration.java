package com.aerofs.devman.server.config;

import com.aerofs.lib.LibParam.REDIS;

public class RedisConfiguration
{
    public String getHost() {
        return REDIS.AOF_ADDRESS.getHostName();
    }

    public int getPort() {
        return REDIS.AOF_ADDRESS.getPort();
    }
}

package com.aerofs.devman.server.config;

import com.aerofs.base.BaseParam.Verkehr;

public class VerkehrConfiguration
{
    public String getHost() {
        return Verkehr.HOST.get();
    }

    public short getPort() {
        return 9019;
    }
}

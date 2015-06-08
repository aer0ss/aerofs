package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.BaseParam;

import javax.inject.Inject;
import java.net.InetSocketAddress;

public class ZephyrParams {
    public final InetSocketAddress serverAddress;

    @Inject
    public ZephyrParams()
    {
        serverAddress = BaseParam.Zephyr.SERVER_ADDRESS;
    }

    public ZephyrParams(InetSocketAddress address)
    {
        serverAddress = address;
    }
}

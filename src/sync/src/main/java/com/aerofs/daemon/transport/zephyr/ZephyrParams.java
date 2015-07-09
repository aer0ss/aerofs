package com.aerofs.daemon.transport.zephyr;

import javax.inject.Inject;
import java.net.InetSocketAddress;

import static com.aerofs.base.config.ConfigurationProperties.getAddressProperty;

public class ZephyrParams {
    public final InetSocketAddress serverAddress;

    @Inject
    public ZephyrParams()
    {
        this(getSocketAddressFromConfiguration());
    }

    public ZephyrParams(InetSocketAddress address)
    {
        serverAddress = address;
    }

    private static InetSocketAddress getSocketAddressFromConfiguration()
    {
        return getAddressProperty("base.zephyr.address",
                InetSocketAddress.createUnresolved("relay.aerofs.com", 443));
    }
}

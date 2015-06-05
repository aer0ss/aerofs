package com.aerofs.daemon.transport.ssmp;

import com.aerofs.base.BaseParam.SSMP;

import javax.inject.Inject;
import java.net.InetSocketAddress;

public class SSMPParams {
    public final InetSocketAddress serverAddress;

    @Inject
    public SSMPParams() {
        this(SSMP.SERVER_ADDRESS);
    }

    public SSMPParams(InetSocketAddress address) {
        serverAddress = address;
    }
}

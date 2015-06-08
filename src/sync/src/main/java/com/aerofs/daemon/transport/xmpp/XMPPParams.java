package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.BaseParam.XMPP;

import javax.inject.Inject;
import java.net.InetSocketAddress;

public class XMPPParams {
    public final InetSocketAddress serverAddress;
    public final String serverDomain;

    @Inject
    public XMPPParams() {
        serverAddress = XMPP.SERVER_ADDRESS;
        serverDomain = XMPP.getServerDomain();
    }

    public XMPPParams(InetSocketAddress address, String domain) {
        serverAddress = address;
        serverDomain = domain;
    }
}

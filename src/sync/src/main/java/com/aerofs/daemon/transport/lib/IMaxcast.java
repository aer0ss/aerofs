package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.SID;

public interface IMaxcast
{
    // TODO similar to IUnicast, don't distinguish payload and control message
    // here.
    // TODO distinguish client vs server as in unicast
    void sendPayload(SID sid, int mcastid, byte[] bs) throws Exception;
}

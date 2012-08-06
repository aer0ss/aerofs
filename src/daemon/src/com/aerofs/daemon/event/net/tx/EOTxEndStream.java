package com.aerofs.daemon.event.net.tx;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.id.StreamID;

public class EOTxEndStream extends AbstractEBIMC
{
    public final StreamID _streamId;

    public EOTxEndStream(StreamID streamId, IIMCExecutor imce)
    {
        super(imce);
        _streamId = streamId;
    }

    @Override
    public String toString()
    {
        return "txEndStrm " + _streamId;
    }
}

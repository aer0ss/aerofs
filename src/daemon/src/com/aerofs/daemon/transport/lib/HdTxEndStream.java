package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOTxEndStream;
import com.aerofs.lib.event.Prio;

public class HdTxEndStream implements IEventHandler<EOTxEndStream> {

    private final StreamManager streamManager;

    public HdTxEndStream(StreamManager streamManager)
    {
        this.streamManager = streamManager;
    }

    @Override
    public void handle_(EOTxEndStream ev, Prio prio)
    {
        streamManager.removeOutgoingStream(ev._streamId);
    }
}

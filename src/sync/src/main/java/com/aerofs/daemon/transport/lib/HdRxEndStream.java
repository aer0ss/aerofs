package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.lib.event.Prio;

public class HdRxEndStream implements IEventHandler<EORxEndStream>
{
    private final StreamManager streamManager;

    public HdRxEndStream(StreamManager streamManager)
    {
        this.streamManager = streamManager;
    }

    @Override
    public void handle_(EORxEndStream ev, Prio prio)
    {
        streamManager.removeIncomingStream(ev._did, ev._sid);
    }
}

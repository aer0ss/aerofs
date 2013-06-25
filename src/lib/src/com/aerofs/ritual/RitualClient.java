package com.aerofs.ritual;

import com.aerofs.proto.Ritual.RitualServiceStub;

import java.io.Closeable;

/**
 * This is a future-based, asynchronous client interface to Ritual.
 * All Ritual calls made with this class are asynchronous and return a future that will be set
 * with the result.
 *
 * This interface should be preferred over the synchronous interface, and this is why we choose
 * to name it simply "RitualClient".
 */
public class RitualClient extends RitualServiceStub implements Closeable
{
    private final RitualClientHandler _handler;

    RitualClient(RitualClientHandler handler)
    {
        super(handler);
        _handler = handler;
    }

    @Override
    public void close()
    {
        _handler.disconnect();
    }
}

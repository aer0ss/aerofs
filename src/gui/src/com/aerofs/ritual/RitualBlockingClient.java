package com.aerofs.ritual;

import com.aerofs.proto.Ritual.RitualServiceBlockingStub;

import java.io.Closeable;

/**
 * This is a synchronous client interface to Ritual
 * All Ritual calls made with this class are be synchronous, ie, they block until the complete
 * or fail.
 *
 * We choose to name it "RitualBlockingClient" rather than "RitualSyncClient" because "Sync" is an
 * overloaded term (file syncing, etc...)
 */
public class RitualBlockingClient extends RitualServiceBlockingStub implements Closeable
{
    private final RitualClientHandler _handler;

    RitualBlockingClient(RitualClientHandler handler)
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

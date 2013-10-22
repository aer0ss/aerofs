/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelDIDProvider;

final class DIDWrapper implements ChannelDIDProvider
{
    private final DID did;

    DIDWrapper(DID did)
    {
        this.did = did;
    }

    @Override
    public DID getRemoteDID()
    {
        return did;
    }
}

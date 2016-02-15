/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ritual;

public interface IRitualClientProvider
{
    RitualBlockingClient getBlockingClient();

    RitualClient getNonBlockingClient();
}

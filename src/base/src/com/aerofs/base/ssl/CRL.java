/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import java.util.HashSet;
import java.util.List;

/**
 * Class to hold revoked serials so that the netty SSL hooks can verify client devices.
 */
public class CRL
{
    // Use a hashset so we can look up serial numbers quickly.
    private final HashSet<Long> _serialsHashSet = new HashSet<Long>();

    synchronized public void addRevokedSerials(List<Long> serials)
    {
        _serialsHashSet.addAll(serials);
    }

    synchronized public boolean contains(long serial)
    {
        return _serialsHashSet.contains(serial);
    }
}
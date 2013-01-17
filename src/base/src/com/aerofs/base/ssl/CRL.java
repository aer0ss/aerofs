/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.ssl.ITrustManagerProvider;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManager;
import java.util.HashSet;
import java.util.List;

/**
 * Class to hold revoked serials so that the netty SSL hooks can verify client devices.
 */
public class CRL implements ITrustManagerProvider
{
    // Use a hashset so we can look up serial numbers quickly.
    private HashSet<Long> _serialsHashSet = new HashSet<Long>();

    // Trust managers.
    private final TrustManager[] _tms = {new CRLBasedTrustManager(this)};

    synchronized public void addRevokedSerials(List<Long> serials)
    {
        _serialsHashSet.addAll(serials);
    }

    synchronized public boolean contains(long serial)
    {
        return _serialsHashSet.contains(serial);
    }

    @Override
    @Nullable
    public TrustManager[] getTrustManagers()
    {
        return _tms;
    }
}
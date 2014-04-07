/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * contains a list of revoked SSL certificate serials.
 * <p/>
 * This class is thread-safe.
 */
public class CRL
{
    private final CopyOnWriteArraySet<Long> revokedSerials = Sets.newCopyOnWriteArraySet();

    public void addRevokedSerials(Collection<Long> serials)
    {
        revokedSerials.addAll(serials);
    }

    public boolean contains(long serial)
    {
        return revokedSerials.contains(serial);
    }
}
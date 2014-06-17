/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.protocol;

import java.security.MessageDigest;

/**
 * Dummy MessageDigest implementation that always return null
 */
class NullDigest extends MessageDigest
{
    public NullDigest()
    {
        super("NULL");
    }

    @Override
    protected void engineUpdate(byte b)
    {}

    @Override
    protected void engineUpdate(byte[] bytes, int i, int i2)
    {}

    @Override
    protected byte[] engineDigest()
    {
        return null;
    }

    @Override
    protected void engineReset()
    {}
}

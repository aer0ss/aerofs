/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;


public class Quota
{
    public final Long bytesUsed;
    public final Long bytesAllowed;

    public Quota(Long bytesUsed, Long bytesAllowed)
    {
        this.bytesUsed = bytesUsed;
        this.bytesAllowed = bytesAllowed;
    }
}

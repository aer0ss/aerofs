/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.ids.DID;
import com.aerofs.daemon.core.ex.ExWrapped;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nullable;

/**
 * Exception used by the download subsystem to indicate that an object could not be downloaded
 * due to errors in the dependency chain
 */
public class ExUnsatisfiedDependency extends ExWrapped
{
    private static final long serialVersionUID = 0L;

    @Nullable final DID _did;
    public final SOCID _socid;

    ExUnsatisfiedDependency(SOCID socid, @Nullable DID did, Exception e)
    {
        super(e);
        _socid = socid;
        _did = did;
    }
}

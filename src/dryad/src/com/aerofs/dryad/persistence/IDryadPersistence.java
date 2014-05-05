/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.persistence;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Range;

import java.io.InputStream;

/**
 * Right now there's only local file based persistence. In the future, we may extend to other
 * methods of persistence.
 */
public interface IDryadPersistence
{
    void putApplianceLogs(long customerID, UniqueID dryadID, InputStream src, Range<Long> range)
        throws Exception;
    void putClientLogs(long customerID, UniqueID dryadID, UserID userID, DID deviceID,
            InputStream src, Range<Long> range) throws Exception;
}

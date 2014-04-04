/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.storage;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import java.io.InputStream;

public class ClientLogsDryadDatabase extends AbstractDryadDatabase
{
    public ClientLogsDryadDatabase(String storageDirectory)
    {
        super(storageDirectory);
    }

    public boolean logsExist(long orgID, UniqueID dryadID, UserID userID, DID deviceID)
    {
        // TODO
        return false;
    }

    public void putLogs(long orgID, UniqueID dryadID, UserID userID, DID deviceID,
            InputStream is)
    {
        // TODO
    }
}
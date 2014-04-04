/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.storage;

import com.aerofs.base.id.UniqueID;

import java.io.InputStream;

public class ApplianceLogsDryadDatabase extends AbstractDryadDatabase
{
    public ApplianceLogsDryadDatabase(String storageDirectory)
    {
        super(storageDirectory);
    }

    public boolean logsExist(long orgID, UniqueID dryadID)
    {
        // TODO
        return false;
    }

    public void putLogs(long orgID, UniqueID dryadID, InputStream is)
    {
        // TODO
    }
}
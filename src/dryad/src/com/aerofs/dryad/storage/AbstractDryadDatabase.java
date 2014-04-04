/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.storage;

public class AbstractDryadDatabase
{
    protected final String _storageDirectory;

    public AbstractDryadDatabase(String storageDirectory)
    {
        _storageDirectory = storageDirectory;
    }
}
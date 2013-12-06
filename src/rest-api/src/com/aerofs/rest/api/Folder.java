/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

/**
 * Contains detailed metadata for a single folder.
 */
public class Folder extends CommonMetadata
{
    // whether this folder is an anchor
    public final boolean is_shared;

    public Folder(String id, String name, boolean is_shared)
    {
        super(id, name);
        this.is_shared = is_shared;
    }
}

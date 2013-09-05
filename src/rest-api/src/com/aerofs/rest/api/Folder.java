/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

/**
 * Contains detailed metadata for a single folder.
 */
public class Folder
{
    // basename of the file
    public final String name;

    // folder id
    public final String id;

    // whether this folder is an anchor
    public final boolean is_shared;

    public Folder(String name, String id, boolean is_shared)
    {
        this.name = name;
        this.id = id;
        this.is_shared = is_shared;
    }
}

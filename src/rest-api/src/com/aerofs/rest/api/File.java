/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import java.util.Date;

/**
 * Represents metadata for a single file
 */
public class File
{
    // basename of the file
    public final String name;

    // file id
    public final String id;

    // last modified time of this file
    public final Date last_modified;

    // size of file in bytes
    public final long size;

    public File(String name, String id, Date last_modified, long size)
    {
        this.name = name;
        this.id = id;
        this.size = size;
        this.last_modified = last_modified;
    }
}

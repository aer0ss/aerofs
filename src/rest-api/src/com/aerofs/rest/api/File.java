/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import java.util.Comparator;
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

    // MIME type
    public final String mime_type;

    public File(String name, String id, Date last_modified, long size, String mime_type)
    {
        this.name = name;
        this.id = id;
        this.size = size;
        this.last_modified = last_modified;
        this.mime_type = mime_type;
    }

    public static final Comparator<File> BY_NAME = new Comparator<File>() {
        @Override
        public int compare(File o1, File o2)
        {
            return o1.name.compareToIgnoreCase(o2.name);
        }
    };
}

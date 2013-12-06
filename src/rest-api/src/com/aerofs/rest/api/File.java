/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * Represents metadata for a single file
 */
public class File extends CommonMetadata
{
    // last modified time of this file
    public final @Nullable Date last_modified;

    // size of file in bytes
    public final @Nullable Long size;

    // MIME type
    public final String mime_type;

    public File(String id, String name, @Nullable Date last_modified, @Nullable Long size,
            String mime_type)
    {
        super(id, name);
        this.size = size;
        this.last_modified = last_modified;
        this.mime_type = mime_type;
    }
}

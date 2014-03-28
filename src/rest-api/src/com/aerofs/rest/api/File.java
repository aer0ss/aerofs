/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import com.aerofs.base.NoObfuscation;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * Represents metadata for a single file
 */
@NoObfuscation
public class File extends CommonMetadata
{
    // last modified time of this file
    public final @Nullable Date last_modified;

    // size of file in bytes
    public final @Nullable Long size;

    // MIME type
    public final String mime_type;

    // Content ETag
    public final String etag;

    public File(String id, String name, @Nullable Date last_modified, @Nullable Long size,
            String mime_type, String etag)
    {
        this(id, name, null, null, last_modified, size, mime_type, etag);
    }

    public File(String id, String name, String parent, @Nullable Date last_modified,
            @Nullable Long size, String mime_type, String etag)
    {
        this(id, name, parent, null, last_modified, size, mime_type, etag);
    }

    public File(String id, String name, String parent, @Nullable ParentPath path,
            @Nullable Date last_modified, @Nullable Long size, String mime_type, String etag)
    {
        super(id, name, parent, path);
        this.size = size;
        this.last_modified = last_modified;
        this.mime_type = mime_type;
        this.etag = etag;
    }
}

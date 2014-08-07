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

    public enum ContentState
    {
        AVAILABLE,
        SYNCING,
        DESELECTED,
        INSUFFICIENT_STORAGE,
    }

    // machine-readable
    public final @Nullable String content_state;

    public File(String id, String name, String parent, ContentState state,
            String mime_type, String etag, @Nullable ParentPath path)
    {
        this(id, name, parent, path, null, null, mime_type, etag, state);
    }

    public File(String id, String name, String parent, @Nullable ParentPath path,
            @Nullable Date last_modified, @Nullable Long size, String mime_type, String etag,
            ContentState state)
    {
        super(id, name, parent, path);
        this.size = size;
        this.last_modified = last_modified;
        this.mime_type = mime_type;
        this.etag = etag;
        this.content_state = state.toString();
    }
}

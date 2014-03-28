/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import com.aerofs.base.NoObfuscation;

import javax.annotation.Nullable;

/**
 * Contains detailed metadata for a single folder.
 */
@NoObfuscation
public class Folder extends CommonMetadata
{
    // whether this folder is an anchor
    public final boolean is_shared;

    public final @Nullable String sid;

    public final @Nullable ChildrenList children;

    public Folder(String id, String name, @Nullable String sid)
    {
        this(id, name, null, null, sid, null);
    }

    public Folder(String id, String name, String parent, @Nullable String sid)
    {
        this(id, name, parent, null, sid, null);
    }

    public Folder(String id, String name, String parent, @Nullable ParentPath path,
            @Nullable String sid, @Nullable ChildrenList children)
    {
        super(id, name, parent, path);
        this.is_shared = sid != null;
        this.sid = sid;
        this.children = children;
    }
}

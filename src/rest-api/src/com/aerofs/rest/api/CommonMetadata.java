/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import com.aerofs.base.NoObfuscation;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Metadata shared by {@link File} and {@link Folder}
 */
@NoObfuscation
public class CommonMetadata
{
    // object id
    public final String id;

    // object basename
    public final String name;

    // parent object id
    public final @Nullable String parent;

    // path
    public final @Nullable ParentPath path;

    public CommonMetadata(String id, String name)
    {
        this(id, name, null, null);
    }

    protected CommonMetadata(String id, String name, @Nullable String parent,
            @Nullable ParentPath path)
    {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.path = path;
    }

    public static CommonMetadata child(String parent, String name)
    {
        return new CommonMetadata(null, name, parent, null);
    }

    public static final Comparator<CommonMetadata> BY_NAME = new Comparator<CommonMetadata>() {
        @Override
        public int compare(CommonMetadata o1, CommonMetadata o2)
        {
            return o1.name.compareTo(o2.name);
        }
    };
}

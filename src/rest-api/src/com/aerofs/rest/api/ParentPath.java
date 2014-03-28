/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

import com.aerofs.base.NoObfuscation;

import java.util.List;

/**
 * Represents a the collection of Folder that make up the full path to the parent
 * folder of an object.
 *
 * The path elements are ordered starting from the root down to the innermost Folder.
 */
@NoObfuscation
public class ParentPath
{
    public final List<Folder> folders;

    public ParentPath(List<Folder> folders)
    {
        this.folders = folders;
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.api;

import java.util.Comparator;

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

    public static final Comparator<Folder> BY_NAME = new Comparator<Folder>() {
        @Override
        public int compare(Folder o1, Folder o2)
        {
            return o1.name.compareToIgnoreCase(o2.name);
        }
    };
}

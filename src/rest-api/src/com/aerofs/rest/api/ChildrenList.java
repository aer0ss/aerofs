package com.aerofs.rest.api;

import java.util.List;

/**
 * Represent a listing of the contents of a folder
 */
public class ChildrenList
{
    public final String parent;
    public final List<Folder> folders;
    public final List<File> files;

    public ChildrenList(String parent, List<Folder> folders, List<File> files)
    {
        this.parent = parent;
        this.folders = folders;
        this.files = files;
    }
}

package com.aerofs.rest.api;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represent a listing of the contents of a folder
 */
public class ChildrenList
{
    // NB: deprecated in 1.2
    public final @Nullable String parent;

    public final List<Folder> folders;
    public final List<File> files;

    public ChildrenList(@Nullable String parent, List<Folder> folders, List<File> files)
    {
        this.parent = parent;
        this.folders = folders;
        this.files = files;
    }
}

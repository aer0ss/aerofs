package com.aerofs.rest.api;

import java.util.List;

/**
 * Represent a listing of the contents of a folder
 */
public class Listing
{
    public final List<Folder> folders;
    public final List<File> files;

    public Listing(List<Folder> folders, List<File> files)
    {
        this.folders = folders;
        this.files = files;
    }
}


package com.aerofs.daemon.core.phy.s3;

import com.aerofs.lib.Path;

import java.io.FileNotFoundException;

class PathNotFoundException extends FileNotFoundException
{
    private static final long serialVersionUID = 1L;

    PathNotFoundException(Path path)
    {
        super("Path not found: " + path);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import java.io.File;

public class ExFileNoPerm extends ExFileIO
{
    private static final long serialVersionUID = 1L;
    public ExFileNoPerm(File f)
    {
        super("No permissions on {}", f);
    }
}

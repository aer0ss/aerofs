/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.shell;

import org.apache.commons.cli.Options;

public abstract class AbstractShellCommand<T> implements IShellCommand<T>
{
    private static final Options EMPTY_OPTS = new Options();

    @Override
    public boolean isHidden()
    {
        return false;
    }

    @Override
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return EMPTY_OPTS;
    }

    @Override
    public String getFooter()
    {
        return "";
    }
}

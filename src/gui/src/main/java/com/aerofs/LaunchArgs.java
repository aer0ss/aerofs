/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Class that holds the list of JVM args that must be passed on to the daemon. They are used, if at
 * all when running the GUI or the CLI.
 */
public class LaunchArgs
{
    private List<String> _args;

    public LaunchArgs()
    {
        _args = Lists.newArrayList();
    }

    public LaunchArgs(List<String> args)
    {
        _args = args;
    }

    public void addArg(String arg)
    {
        _args.add(arg);
    }

    public List<String> getArgs()
    {
        return _args;
    }
}
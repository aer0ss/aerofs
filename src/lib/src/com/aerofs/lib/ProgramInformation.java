/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

/**
 * This class returns information about the currently running program (daemon, gui, cli, sh, etc...)
 */
public final class ProgramInformation
{
    private static ProgramInformation _programInformation;

    private final String _programName;

    public static ProgramInformation get()
    {
        return _programInformation;
    }

    public static void init_(String programName)
    {
        if (_programInformation == null) {
            _programInformation = new ProgramInformation(programName);
        }
    }

    private ProgramInformation(String programName)
    {
        this._programName = programName;
    }

    public String getProgramName()
    {
        return _programName;
    }
}

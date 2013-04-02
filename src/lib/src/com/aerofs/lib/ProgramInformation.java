/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ProgramInformation
{
    private static ProgramInformation _programInformation;

    private final String _programName;

    public static ProgramInformation getCurrent()
    {
        return checkNotNull(_programInformation);
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

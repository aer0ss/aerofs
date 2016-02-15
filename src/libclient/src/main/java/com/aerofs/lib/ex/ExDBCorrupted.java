/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

/**
 * corrupted db should be fatal, hence the use of Error as base class
 */
public class ExDBCorrupted extends Error
{
    private static final long serialVersionUID = 0L;

    public final String _integrityCheckResult;

    public ExDBCorrupted(String integrityCheckResult) { _integrityCheckResult = integrityCheckResult; }
}

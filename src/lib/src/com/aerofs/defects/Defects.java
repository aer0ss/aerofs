/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.ids.UserID;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.aerofs.defects.DefectFactory.newFactory;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The purpose of this class is to act as a static wrapper to a global defect factory. It should
 * redirect all of its methods to the actual instance so that we can:
 * 1 - mock the instance in unit tests.
 * 2 - unit test the defect factory instance separately.
 */
public class Defects
{
    // protected so that test suites can override it
    private static DefectFactory _factory;

    // Main calls this to initialize the default defect system.
    public static void init(String programName, String rtroot)
            throws IOException, GeneralSecurityException
    {
        setFactory(newFactory(programName, rtroot));
    }

    // used by frequent, command, and priority defects
    protected static DefectFactory getFactory()
    {
        return checkNotNull(_factory);
    }

    // unit test calls this to replace the factory with a mock
    protected static void setFactory(DefectFactory factory)
    {
        _factory = factory;
    }

    /**
     * see {@link com.aerofs.defects.DefectFactory#newMetric(String)}
     */
    public static Defect newMetric(String name)
    {
        return getFactory().newMetric(name);
    }

    /**
     * see {@link com.aerofs.defects.DefectFactory#newDefect(String)}
     */
    public static Defect newDefect(String name)
    {
        return getFactory().newDefect(name);
    }

    public static Defect newDefectWithLogs(String name)
    {
        return getFactory().newDefectWithLogs(name);
    }

    public static Defect newDefectWithLogsNoCfg(String name, UserID userID, String absRTRoot)
    {
        return getFactory().newDefectWithLogsNoCfg(name, userID, absRTRoot);
    }

    public static Defect newFrequentDefect(String name)
    {
        return getFactory().newFrequentDefect(name);
    }

    public static Defect newUploadCoreDatabase()
    {
        return getFactory().newUploadCoreDatabase();
    }

    private Defects()
    {
        // prevents instantiation
    }
}

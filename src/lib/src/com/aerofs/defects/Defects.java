/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.id.UserID;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.aerofs.defects.DefectFactory.newFactory;
import static com.google.common.base.Preconditions.checkNotNull;

public class Defects
{
    // protected so that test suites can override it
    private static DefectFactory _factory;

    // Main calls this to initialize the default defect system.
    public static void init(String programName, String rtroot, boolean isPrivateDeployment)
            throws IOException, GeneralSecurityException
    {
        setFactory(newFactory(programName, rtroot, isPrivateDeployment));
    }

    private static DefectFactory getFactory()
    {
        return checkNotNull(_factory);
    }

    // unit test calls this to replace the factory with a mock
    protected static void setFactory(DefectFactory factory)
    {
        _factory = factory;
    }

    /**
     * See {@link com.aerofs.defects.DefectFactory#newDefect(String)}
     */
    public static Defect newDefect(String name)
    {
        return getFactory().newDefect(name);
    }

    // for command defect
    protected static Defect newDefect(String name, DryadClient dryad)
    {
        return getFactory().newDefect(name, dryad);
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

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.ids.UserID;
import com.aerofs.defects.Defect.Priority;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.lib.cfg.InjectableCfg;
import com.google.common.collect.Queues;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Maps.fromProperties;

public class DefectFactory
{
    private final Executor _executor;
    private final RecentExceptions _recentExceptions;
    private final InjectableCfg _cfg;
    private final RockLog _rockLog;
    private final DryadClient _dryad;
    private final Map<String, String> _properties;

    public DefectFactory(Executor executor, RecentExceptions recentExceptions,
            InjectableCfg cfg, Map<String, String> properties, RockLog rockLog, DryadClient dryad)
    {
        _executor = executor;
        _recentExceptions = recentExceptions;
        _cfg = cfg;
        _rockLog = rockLog;
        _dryad = dryad;
        _properties = properties;
    }

    // Also used by other types of defects in the same package.
    protected AutoDefect newAutoDefect(String name)
    {
        return newAutoDefect(name, _dryad);
    }

    // Used by command defects.
    protected AutoDefect newAutoDefect(String name, DryadClient dryad)
    {
        return new AutoDefect(name, _cfg, _rockLog, dryad, _executor, _recentExceptions,
                _properties);
    }

    /**
     * A simple defect that only sends a small defect report to RockLog
     *
     * The defect name allows us to easily search and aggregate defects in RockLog.
     *
     * How to pick a good defect name:
     *
     * - NO SPACES
     * - Short string that describes what component failed
     * - Use dots to create hierarchies
     *
     * Good names:
     * "daemon.linker.someMethod", "system.nolaunch"
     *
     * Bad names:
     * "Name With Spaces", "daemon.linker.someMethod_failed" <-- "failed" is redundant
     */
    public Defect newMetric(String name)
    {
        return new Defect(name, _cfg, _rockLog, _executor);
    }

    /**
     * see {@link #newMetric(String)}
     */
    public Defect newDefect(String name)
    {
        return newAutoDefect(name)
                .setFilesToUpload(AutoDefect.UPLOAD_NONE);
    }

    public Defect newDefectWithLogs(String name)
    {
        return newAutoDefect(name)
                .setFilesToUpload(AutoDefect.UPLOAD_LOGS | AutoDefect.UPLOAD_HEAP_DUMPS);
    }

    public Defect newDefectWithLogsNoCfg(String name, UserID userID, String absRTRoot)
    {
        return newAutoDefect(name)
                .setUserID(userID)
                .setAbsRTRoot(absRTRoot)
                .setFilesToUpload(AutoDefect.UPLOAD_LOGS | AutoDefect.UPLOAD_HEAP_DUMPS);
    }

    public Defect newFrequentDefect(String name)
    {
        return new FrequentDefect(name, _cfg, _rockLog, _dryad, _executor, _recentExceptions,
                _properties)
                .setFilesToUpload(AutoDefect.UPLOAD_LOGS | AutoDefect.UPLOAD_HEAP_DUMPS);
    }

    public Defect newUploadCoreDatabase()
    {
        return newAutoDefect("upload_core_database")
                .setPriority(Priority.Command)
                .setFilesToUpload(AutoDefect.UPLOAD_DB);
    }

    // Achievement unlocked: FactoryFactory.
    public static DefectFactory newFactory(String programName, String rtroot)
            throws IOException, GeneralSecurityException
    {
        Executor executor = new ThreadPoolExecutor(
                0, 1,                                           // at most 1 thread
                1, TimeUnit.MINUTES,                            // frees up idle threads
                Queues.<Runnable>newLinkedBlockingQueue(100),   // bounded event queue
                // TODO: are we actually OK with potentially blocking the daemon with this?
                new CallerRunsPolicy()                          // blocks caller on overflow
        );

        RecentExceptions recentExceptions = new RecentExceptions(programName, rtroot,
                RecentExceptions.DEFAULT_INTERVAL, new TimeSource());
        RockLog rockLog = new RockLog.Noop();
        DryadClient dryad = new DryadClient.Noop();

        return new DefectFactory(executor, recentExceptions, new InjectableCfg(),
                fromProperties(System.getProperties()), rockLog, dryad);
    }
}

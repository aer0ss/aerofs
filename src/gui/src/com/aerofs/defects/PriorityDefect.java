/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.defects.Defect.Priority;
import com.aerofs.labeling.L;
import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.google.common.collect.Queues;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;

import static com.aerofs.defects.DefectUtils.newDefectID;
import static com.aerofs.defects.Defects.getFactory;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class PriorityDefect
{
    private static final Logger l = Loggers.getLogger(PriorityDefect.class);

    private final IRitualClientProvider _ritualProvider;
    private final Executor _executor;

    private String _message;
    private Throwable _exception;
    private boolean _expected;
    private String _contactEmail;
    private boolean _sampleCPU;
    private boolean _sendFilenames;

    private PriorityDefect(IRitualClientProvider ritualProvider, Executor executor)
    {
        _ritualProvider = ritualProvider;
        _executor = executor;
    }

    public PriorityDefect setMessage(String message)
    {
        _message = message;
        _sampleCPU = _message.toLowerCase().contains("cpu");
        return this;
    }

    public PriorityDefect setException(@Nullable Throwable exception)
    {
        _exception = exception;
        return this;
    }

    public PriorityDefect setExpected(boolean expected)
    {
        _expected = expected;
        return this;
    }

    public PriorityDefect setContactEmail(String contactEmail)
    {
        _contactEmail = contactEmail;
        return this;
    }

    public PriorityDefect setSendFilenames(boolean sendFilenames)
    {
        _sendFilenames = sendFilenames;
        return this;
    }

    public void sendAsync()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                sendSyncIgnoreErrors();
            }
        });
    }

    public void sendSyncIgnoreErrors()
    {
        String progress = _sampleCPU
                ? "Sampling " + L.product() + " CPU usage"
                : "Submitting";

        UI.get().addProgress(progress, true);

        try {
            sendSync();
            UI.get().notify(MessageType.INFO, "Problem submitted. Thank you!");
        } catch (Exception e) {
            l.warn("Failed to send priority defect:", e);
            UI.get()
                    .notify(MessageType.ERROR,
                            "Failed to report the problem. " + "Please try again later.");
        } finally {
            UI.get().removeProgress(progress);
        }
    }

    private void sendSync()
            throws Exception
    {
        String defectID = newDefectID();

        l.info("Submitting priority defect: {}", defectID);

        if (_contactEmail == null) {
            _contactEmail = Cfg.db().get(Key.CONTACT_EMAIL);
        } else {
            saveContactEmail(_contactEmail);
        }

        if (_sampleCPU) {
            logThreads();
        }

        getFactory().newAutoDefect("defect.priority")
                .setDefectID(defectID)
                .setPriority(Priority.User)
                .setFilesToUpload(AutoDefect.UPLOAD_LOGS | AutoDefect.UPLOAD_HEAP_DUMPS |
                        (_sendFilenames ? AutoDefect.UPLOAD_FILENAMES : AutoDefect.UPLOAD_NONE))
                .setMessage(_message)
                .setException(_exception)
                .addData("daemon_status", getDaemonStatus())
                // for reason unknown, the ElasticSearch's indexer is expecting the field
                // "expected" to be a long. Hence we use the "is_expected" for the field's key.
                .addData("is_expected", _expected)
                .sendSync();

        newMutualAuthClientFactory().create()
                .signInRemote()
                .sendPriorityDefectEmail(defectID, _contactEmail, _message);
    }

    private void saveContactEmail(@Nonnull String contactEmail)
    {
        try {
            Cfg.db().set(Key.CONTACT_EMAIL, contactEmail);
        } catch (SQLException e) {
            l.warn("set contact email, ignored: " + Util.e(e));
        }
    }

    private void logThreads()
    {
        for (int i = 0; i < 20; i++) {
            ThreadUtil.sleepUninterruptable(1 * C.SEC);
            Util.logAllThreadStackTraces();
            try {
                _ritualProvider.getBlockingClient().logThreads();
            } catch (Exception e) {
                l.warn("log daemon threads: " + Util.e(e));
            }
        }
    }

    private String getDaemonStatus()
    {
        try {
            PBDumpStat template = PBDumpStat.newBuilder()
                    .setUpTime(0)
                    .addTransport(PBTransport.newBuilder()
                            .setBytesIn(0)
                            .setBytesOut(0)
                            .addConnection("")
                            .setName("")
                            .setDiagnosis(""))
                    .setMisc("")
                    .build();

            PBDumpStat reply = _ritualProvider.getBlockingClient().dumpStats(template).getStats();

            return Util.realizeControlChars(JsonFormat.prettyPrint(reply));
        } catch (Exception e) {
            return  "(cannot dump daemon status: " + Util.e(e) + ")";
        }
    }

    public static class Factory
    {
        private final Executor _executor = new ThreadPoolExecutor(
                0, 1,                                           // at most 1 thread
                30, TimeUnit.SECONDS,                           // idle threads TTL
                Queues.<Runnable>newLinkedBlockingQueue(10),    // bounded event queue
                // abort on overflow. This is ok because priority defects are initiated by the user.
                new AbortPolicy()
        );

        private final IRitualClientProvider _ritualProvider;

        public Factory(IRitualClientProvider ritualProvider)
        {
            _ritualProvider = ritualProvider;
        }

        public PriorityDefect newPriorityDefect()
        {
            return new PriorityDefect(_ritualProvider, _executor);
        }
    }
}

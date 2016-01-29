/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.defects.Defect.Priority;
import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import static com.aerofs.defects.DefectUtils.newDefectID;
import static com.aerofs.defects.Defects.getFactory;
import static com.aerofs.lib.cfg.ICfgStore.CONTACT_EMAIL;

public abstract class PriorityDefect
{
    private static final Logger l = Loggers.getLogger(PriorityDefect.class);

    protected String    _subject;
    protected String    _message;
    protected String    _contactEmail;
    protected boolean   _sampleCPU;

    private final InjectableSPBlockingClientFactory _spFactory;
    private final Executor _executor;

    protected PriorityDefect(InjectableSPBlockingClientFactory spFactory,
            Executor executor)
    {
        _spFactory = spFactory;
        _executor = executor;
    }

    public PriorityDefect setSubject(String subject)
    {
        _subject = subject;
        return this;
    }

    public PriorityDefect setMessage(String message)
    {
        _message = message;
        _sampleCPU = _message.toLowerCase().contains("cpu");
        return this;
    }

    public PriorityDefect setContactEmail(String contactEmail)
    {
        _contactEmail = contactEmail;
        return this;
    }

    public void sendAsync()
    {
        _executor.execute(this::sendSyncIgnoreErrors);
    }

    // Overridden in UIPriorityDefect to send out notifications.
    public void sendSyncIgnoreErrors()
    {
        try {
            sendSync();
        } catch (Exception e) {
            l.warn("Failed to send priority defect:", e);
        }
    }

    public void sendSync()
            throws Exception
    {
        String defectID = newDefectID();

        l.info("Submitting priority defect: {}", defectID);

        if (_contactEmail == null) {
            _contactEmail = Cfg.db().get(CONTACT_EMAIL);
        } else {
            saveContactEmail(_contactEmail);
        }

        if (_sampleCPU) {
            logThreads();
        }

        Defect defect = getFactory().newAutoDefect("defect.priority")
                .setDefectID(defectID)
                .setPriority(Priority.User)
                .setFilesToUpload(AutoDefect.UPLOAD_LOGS | AutoDefect.UPLOAD_HEAP_DUMPS | AutoDefect.UPLOAD_FILENAMES)
                .setSubject(_subject)
                .setMessage(_message)
                .setException(null)
                .addData("daemon_status", getDaemonStatus());

        // Dump the defect content into the log.
        dumpDefectData(defect);
        defect.sendSync();

        _spFactory.create()
                .signInRemote()
                .sendPriorityDefectEmail(defectID, _contactEmail, _subject, _message);
    }

    private void saveContactEmail(@Nonnull String contactEmail)
    {
        try {
            Cfg.db().set(CONTACT_EMAIL, contactEmail);
        } catch (SQLException e) {
            l.warn("Failed to set contact email, ignored: ", e);
        }
    }

    private void logThreads()
    {
        for (int i = 0; i < 20; i++) {
            ThreadUtil.sleepUninterruptable(1 * C.SEC);
            Util.logAllThreadStackTraces();
            try {
                logThreadsImpl();
            } catch (Exception e) {
                l.warn("Failed to log daemon threads: {}", e.toString());
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

            PBDumpStat reply = getDaemonStatusImpl(template);
            return Util.realizeControlChars(JsonFormat.prettyPrint(reply));
        } catch (Exception e) {
            return "(cannot dump daemon status: " + e.toString() + ")";
        }
    }

    private void dumpDefectData(Defect defect)
    {
        StringBuilder sb = new StringBuilder();

        Map<String, Object> data = defect.getData();
        for (Entry<String, Object> entry : data.entrySet()) {
            sb.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n");
        }

        l.info("Priority defect content:\n{}", sb.toString());
    }

    protected abstract void logThreadsImpl() throws Exception;
    protected abstract PBDumpStat getDaemonStatusImpl(PBDumpStat template) throws Exception;
}

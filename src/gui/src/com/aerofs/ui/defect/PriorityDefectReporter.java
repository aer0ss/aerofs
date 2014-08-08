/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.ui.defect;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.UniqueID;
import com.aerofs.labeling.L;
import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.error.ErrorMessages;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class PriorityDefectReporter
{
    private final Logger l = Loggers.getLogger(PriorityDefectReporter.class);

    private final IRitualClientProvider _ritualProvider;

    public PriorityDefectReporter(IRitualClientProvider ritualProvider)
    {
        _ritualProvider = ritualProvider;
    }

    public void sendPriorityDefect(@Nullable String contactEmail, String message,
            boolean dumpFileNames)
    {
        // update the contact email in Cfg DB if it's supplied
        if (contactEmail != null) {
            setContactEmail(contactEmail);
        }

        contactEmail = Cfg.db().get(Key.CONTACT_EMAIL);

        boolean sampleCPU = message.toLowerCase().contains("cpu");

        String progress = sampleCPU ? "Sampling " + L.product() + " CPU usage" : "Submitting";

        UI.get().addProgress(progress, true);

        if (sampleCPU) {
            logThreads();
        }

        try {
            logAndSendSVDefect(message, dumpFileNames);
            newMutualAuthClientFactory().create()
                    .signInRemote()
                    .sendPriorityDefectEmail(UniqueID.generate().toStringFormal(),
                            contactEmail, message);
            UI.get().notify(MessageType.INFO, "Problem submitted. Thank you!");
        } catch (Exception e) {
            l.warn("submit defect: " + Util.e(e), e);
            UI.get().notify(MessageType.ERROR, "Failed to submit the " +
                    "problem " + ErrorMessages.e2msgDeprecated(e) + ". Please try again.");
        } finally {
            UI.get().removeProgress(progress);
        }
    }

    private void logAndSendSVDefect(String message, boolean dumpFileNames)
            throws Exception
    {
        try {
            // we have to make this call regardless of whether this is a private cloud deployment
            // or hybrid cloud deployment because we need the side-effect of logging.
            SVClient.logSendDefectSync(false, message + "\n" + LibParam.END_OF_DEFECT_MESSAGE, null,
                    getDaemonStatus(), dumpFileNames);
        } catch (Exception e) {
            // suppress the error in private cloud deployments, because SV will not be available
            if (PrivateDeploymentConfig.isHybridDeployment()) {
                throw e;
            }
        }
    }

    private void setContactEmail(@Nonnull String contactEmail)
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
}

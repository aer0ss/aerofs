/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.labeling.L;
import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.error.ErrorMessages;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;

public class CmdDefect extends AbstractShellCommand<ShProgram>
{
    public static void sendDefect(RitualBlockingClient ritual, String message,
            boolean dumpFileNames)
    {
        Logger l = Loggers.getLogger(CmdDefect.class);

        boolean cpuIssue = message.toLowerCase().contains("cpu");

        String progress = cpuIssue ? "Sampling " + L.product() + " CPU usage" : "Submitting";

        UI.get().addProgress(progress, true);

        if (cpuIssue) logThreads(ritual, l);

        String daemonStatus;
        try {
            daemonStatus = dumpFullDaemonStatus(ritual);
        } catch (Exception e) {
            daemonStatus = "(cannot dump daemon status: " + Util.e(e) + ")";
        }

        try {
            SVClient.logSendDefectSync(false, message + "\n" + LibParam.END_OF_DEFECT_MESSAGE, null,
                    daemonStatus, dumpFileNames);
            UI.get().notify(MessageType.INFO, "Problem submitted. Thank you!");
        } catch (Exception e) {
            l.warn("submit defect: " + Util.e(e));
            UI.get().notify(MessageType.ERROR, "Failed to submit the " +
                        "problem " + ErrorMessages.e2msgDeprecated(e) + ". Please try again.");
        } finally {
            UI.get().removeProgress(progress);
        }
    }

    private static void logThreads(RitualBlockingClient ritual, Logger l)
    {
        for (int i = 0; i < 20; i++) {
            ThreadUtil.sleepUninterruptable(1 * C.SEC);
            Util.logAllThreadStackTraces();
            try {
                ritual.logThreads();
            } catch (Exception e) {
                l.warn("log daemon threads: " + Util.e(e));
            }
        }
    }

    private static String dumpFullDaemonStatus(RitualBlockingClient ritual)
            throws Exception
    {
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

        PBDumpStat reply = ritual.dumpStats(template).getStats();

        return Util.realizeControlChars(JsonFormat.prettyPrint(reply));
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        StringBuilder sb = new StringBuilder("(via shell): ");
        if (cl.getArgs().length == 0) {
            throw new ExBadArgs("please provide a brief description of the problem");
        }

        for (String arg : cl.getArgs()) {
            sb.append(arg);
            sb.append(" ");
        }

        sendDefect(s.d().getRitualClient_(), sb.toString(), true);
    }

    @Override
    public String getName()
    {
        return "report";
    }

    @Override
    public String getDescription()
    {
        return "report an issue to the " + L.product() + " team";
    }

    @Override
    public String getOptsSyntax()
    {
        return "DESCRIPTION";
    }
}

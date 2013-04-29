package com.aerofs.shell.restricted;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.JsonFormat;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Ritual.DumpStatsReply;
import com.aerofs.shell.IShellCommand;
import com.aerofs.shell.ShProgram;
import com.aerofs.shell.ShellCommandRunner;

public class CmdDstat implements IShellCommand<ShProgram>
{

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        boolean misc = true;
        boolean nonMisc = true;

        if (cl.hasOption('m')) nonMisc = false;
        if (cl.hasOption('M')) misc = false;

        if (cl.getArgs().length != 0) throw new ExBadArgs();

        PBDumpStat.Builder template = PBDumpStat.newBuilder();

        if (nonMisc) {
            template
                .setUpTime(0)
                .addTransport(PBTransport.newBuilder()
                        .setBytesIn(0)
                        .setBytesOut(0)
                        .addConnection("")
                        .setName("")
                        .setDiagnosis(""));
        }

        if (misc) {
            template.setMisc("");
        }

        DumpStatsReply reply = s.d().getRitualClient_().dumpStats(template.build());
        s.out().println(Util.realizeControlChars(
            JsonFormat.prettyPrint(reply.getStats())));
    }

    @Override
    public String getName()
    {
        return "dstat";
    }

    @Override
    public String getDescription()
    {
        return "dump the daemon's internal state";
    }

    @Override
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return new Options()
            .addOption("m", "misc-only", false, "only show misc fields")
            .addOption("M", "no-misc", false, "do not show misc fields");
    }

}

package com.aerofs.shell.restricted;

import com.aerofs.proto.Ritual.DumpStatsReply;
import com.aerofs.shell.IShellCommand;
import com.aerofs.shell.ShellCommandRunner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBDID2User;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.shell.ShProgram;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.Util;
import com.aerofs.lib.JsonFormat;
import com.google.protobuf.ByteString;

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
                .setLaunchTime(0)
                .addDid2User(PBDID2User.newBuilder()
                        .setDeviceId(ByteString.EMPTY)
                        .setUser(""))
                .addTp(PBTransport.newBuilder()
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

package com.aerofs.shell;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetSyncStatusReply;
import com.aerofs.proto.Ritual.PBSyncStatus;

public class CmdSyncStatus implements IShellCommand<ShProgram> {

    @Override
    public String getName() {
        return "sstat";
    }

    @Override
    public String getDescription() {
        return "Display sync status for a given path";
    }

    @Override
    public String getOptsSyntax() {
        return "[PATH]";
    }

    @Override
    public Options getOpts() {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception {
        String[] args = cl.getArgs();
        if (args.length != 1) throw new ExBadArgs();
        PBPath path = s.d().buildPath_(args[0]);
        GetSyncStatusReply reply = s.d().getRitualClient_().getSyncStatus(path);
        for (PBSyncStatus pbs : reply.getStatusListList()) {
            String usr = pbs.getUserName();
            String dev = pbs.hasDeviceName() ? pbs.getDeviceName() : "N/A";
            s.out().println(usr + " " + dev + " " + pbs.getStatus());
        }
    }

}

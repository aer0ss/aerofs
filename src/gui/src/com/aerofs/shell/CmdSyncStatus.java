package com.aerofs.shell;

import com.aerofs.lib.S;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.base.ex.ExBadArgs;
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
        PBPath path = s.d().buildPBPath_(args[0]);
        GetSyncStatusReply reply = s.d().getRitualClient_().getSyncStatus(path);

        // do not show sync status when servers are known to be down
        if (!reply.getIsServerUp()) {
            s.out().println(S.SYNC_STATUS_DOWN);
            return;
        }

        for (PBSyncStatus pbs : reply.getStatusList()) {
            String userID = pbs.getUserID();
            String displayName = pbs.getDisplayName();
            s.out().println(userID + " " + displayName + " " + pbs.getStatus());
        }
    }

    @Override
    public boolean isHidden()
    {
        return false;
    }
}

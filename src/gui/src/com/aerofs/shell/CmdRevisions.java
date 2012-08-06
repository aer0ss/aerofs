package com.aerofs.shell;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.PBRevision;

public class CmdRevisions implements IShellCommand<ShProgram> {

    @Override
    public String getName() {
        return "revisions";
    }

    @Override
    public String getDescription() {
        return "list revision history for a file";
    }

    @Override
    public String getOptsSyntax() {
        return "PATH";
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

        listRevHistory(s, s.d().buildPathElemList_(args[0]));
    }

    private static void listRevHistory(ShellCommandRunner<ShProgram> s, List<String> path)
            throws Exception
    {
        PBPath parent = ShProgram.buildPath_(path);
        ListRevHistoryReply reply = s.d().getRitualClient_().listRevHistory(parent);

        for (PBRevision rev : reply.getRevisionList()) {
            s.out().println((new String(rev.getIndex().toByteArray(), "UTF-8")) + " " + rev.getMtime() + " " + rev.getLength());
        }
    }
}

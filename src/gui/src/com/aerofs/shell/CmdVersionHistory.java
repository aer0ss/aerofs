/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.PBRevision;

public class CmdVersionHistory implements IShellCommand<ShProgram>
{
    @Override
    public String getName() {
        return "vh";
    }

    @Override
    public String getDescription() {
        return "list version history of a file. see also 'ls -h' and 'export -h' commands";
    }

    @Override
    public String getOptsSyntax() {
        return "FILE_PATH";
    }

    @Override
    public Options getOpts() {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();
        if (args.length != 1) throw new ExBadArgs();

        listRevHistory(s, s.d().buildPathElemList_(args[0]));
    }

    private static void listRevHistory(ShellCommandRunner<ShProgram> s, List<String> path)
            throws Exception
    {
        PBPath parent = ShProgram.buildPath_(path);
        ListRevHistoryReply reply = s.d().getRitualClient_().listRevHistory(parent);

        s.out().println("Version index                   Size      Last Modified");
        s.out().println("-------------------------------------------------------");

        for (PBRevision rev : reply.getRevisionList()) {
            s.out().println((new String(rev.getIndex().toByteArray(), "UTF-8")) + " | " +
                    Util.formatSize(rev.getLength()) + "\t| " +
                    Util.formatAbsoluteTime(rev.getMtime()));
        }
    }
}

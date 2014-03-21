/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.lib.Path;
import org.apache.commons.cli.CommandLine;

import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.PBRevision;

public class CmdSyncHistory extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getDescription() {
        return "list sync history of a file. see also 'ls -h' and 'export -h' commands";
    }

    @Override
    public String getOptsSyntax() {
        return "FILE_PATH";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();
        if (args.length != 1) throw new ExBadArgs();

        listRevHistory(s, s.d().buildPath_(args[0]));
    }

    private static void listRevHistory(ShellCommandRunner<ShProgram> s, Path path)
            throws Exception
    {
        if (path.isEmpty()) throw new ExBadArgs();

        ListRevHistoryReply reply = s.d().getRitualClient_().listRevHistory(path.toPB());

        s.out().println("Version index                   Size      Last Modified");
        s.out().println("-------------------------------------------------------");

        for (PBRevision rev : reply.getRevisionList()) {
            s.out().println((new String(rev.getIndex().toByteArray(), "UTF-8")) + " | " +
                    Util.formatSize(rev.getLength()) + "\t| " +
                    Util.formatAbsoluteTime(rev.getMtime()));
        }
    }
}

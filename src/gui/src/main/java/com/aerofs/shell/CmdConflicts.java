/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.KIndex;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.ListConflictsReply;
import com.aerofs.proto.Ritual.ListConflictsReply.ConflictedPath;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.google.common.collect.Lists;
import org.apache.commons.cli.CommandLine;

import java.util.List;

/**
 * no arg: list all conflict branches
 * 1+ arg: list conflict branches for the given file(s)
 */
public class CmdConflicts extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "conflicts";
    }

    @Override
    public String getDescription()
    {
        return "list conflict branches. see also 'export -b' and 'resolve' commands";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH]";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();

        RitualBlockingClient r = s.d().getRitualClient_();

        boolean printPath = args.length != 1;
        List<PBPath> pathList = Lists.newArrayList();
        if (args.length > 0) {
            for (String arg : args) pathList.add(s.d().buildPBPath_(arg));
        } else {
            ListConflictsReply reply = r.listConflicts();
            for (ConflictedPath p : reply.getConflictList()) pathList.add(p.getPath());
        }

        s.out().println("Branch    Size    Last Modified");
        s.out().println("-------------------------------");

        for (PBPath p : pathList) {
            if (printPath) s.out().println(Path.fromPB(p).toString());
            PBObjectAttributes attr = r.getObjectAttributes(p).getObjectAttributes();
            for (PBBranch b : attr.getBranchList()) {
                if (b.getKidx() == KIndex.MASTER.getInt()) continue;

                StringBuilder bd = new StringBuilder();
                if (printPath) bd.append("  ");

                bd.append(b.getKidx()).append(" | ")
                        .append(Util.formatSize(b.getLength())).append(" | ")
                        .append(Util.formatAbsoluteTime(b.getMtime()));

                s.out().println(bd.toString());
            }
        }
    }
}

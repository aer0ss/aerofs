/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.id.KIndex;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdResolve extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "resolve";
    }

    @Override
    public String getDescription()
    {
        return "resolve conflict by removing all branches but one (default to local copy)";
    }

    @Override
    public String getOptsSyntax()
    {
        return "PATH";
    }

    @Override
    public Options getOpts()
    {
        return new Options()
                .addOption("b", "branch", true, "resolve conflict by keeping alternate branch" +
                        " instead of the local one");
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();
        if (args.length != 1) throw new ExBadArgs();

        PBPath path = s.d().buildPBPath_(args[0]);
        RitualBlockingClient r = s.d().getRitualClient_();

        String branch = cl.getOptionValue('b');

        if (branch != null) {
            // export branch contents and import them into MASTER branch
            r.importFile(path, r.exportConflict(path, Integer.valueOf(branch)).getDest());
        }

        // remove all conflict branches
        PBObjectAttributes attr = r.getObjectAttributes(path).getObjectAttributes();
        for (PBBranch b : attr.getBranchList()) {
            if (b.getKidx() == KIndex.MASTER.getInt()) continue;
            r.deleteConflict(path, b.getKidx());
        }
    }
}

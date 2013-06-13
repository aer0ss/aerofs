/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.base.id.SID;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.Map.Entry;

public class CmdRoots implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "roots";
    }

    @Override
    public String getDescription()
    {
        return "list all AeroFS roots. Cross-root pathes are of the form rootid:/absolute/path";
    }

    @Override
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (Cfg.storageType() == StorageType.LINKED) {
            s.out().println("             Root id             |       Absolute Path       ");
        } else {
            s.out().println("             Root id             |            Name           ");
        }
        s.out().println("-------------------------------------------------------------");
        for (Entry<SID, String> e : s.d().getRoots().entrySet()) {
            s.out().println(e.getKey().toStringFormal() + " | " + e.getValue());
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.ids.SID;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import org.apache.commons.cli.CommandLine;

import java.util.Map.Entry;

public class CmdRoots extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "roots";
    }

    @Override
    public String getDescription()
    {
        return "list all AeroFS roots. Cross-root paths are of the form \"rootid:/absolute/path\"";
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

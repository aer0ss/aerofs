/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;

import java.io.File;

public class CmdImport extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "import";
    }

    @Override
    public String getDescription()
    {
        return "import a file from the local file system";
    }

    @Override
    public String getOptsSyntax()
    {
        return "SOURCE DEST";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();
        if (args.length != 2) throw new ExBadArgs("Expected two arguments");

        File source = new File(args[0]);
        PBPath dest = s.d().buildPBPath_(args[1]);

        s.d().getRitualClient_().importFile(dest, source.getAbsolutePath());
    }
}

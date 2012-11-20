/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.io.File;

public class CmdImport implements IShellCommand<ShProgram>
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
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        String[] args = cl.getArgs();
        if (args.length != 2) throw new ExBadArgs("Expected two arguments");

        File source = new File(args[0]);
        Path dest = new Path(s.d().buildPathElemList_(args[1]));

        s.d().getRitualClient_().importFile(dest.toPB(), source.getAbsolutePath());
    }
}

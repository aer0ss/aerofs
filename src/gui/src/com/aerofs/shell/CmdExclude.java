package com.aerofs.shell;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdExclude implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        PBPath path = s.d().buildPath_(cl.getArgs()[0]);
        s.d().getRitualClient_().excludeFolder(path);
    }

    @Override
    public String getName()
    {
        return "exclude";
    }

    @Override
    public String getDescription()
    {
        return "exclude a folder from syncing with other devices";
    }

    @Override
    public String getOptsSyntax()
    {
        return "FOLDER";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

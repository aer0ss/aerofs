package com.aerofs.shell;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.base.ex.ExBadArgs;


public class CmdPause implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        s.d().getRitualClient_().pauseSyncing();
    }

    @Override
    public String getName()
    {
        return "pause";
    }

    @Override
    public String getDescription()
    {
        return "pause syncing activities";
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
}

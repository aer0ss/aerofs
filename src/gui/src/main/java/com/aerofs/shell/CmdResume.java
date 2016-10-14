package com.aerofs.shell;

import org.apache.commons.cli.CommandLine;

import com.aerofs.base.ex.ExBadArgs;


public class CmdResume extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        s.d().getRitualClient_().resumeSyncing();
    }

    @Override
    public String getName()
    {
        return "resume";
    }

    @Override
    public String getDescription()
    {
        return "resume syncing activities";
    }
}

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;

public class CmdExclude extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        s.d().getRitualClient_().excludeFolder(s.d().buildPBPath_(cl.getArgs()[0]));
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
}

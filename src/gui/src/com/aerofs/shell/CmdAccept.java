package com.aerofs.shell;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.ex.ExBadArgs;

public class CmdAccept implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        for (String arg : cl.getArgs()) {
            s.d().getRitualClient_().joinSharedFolder(arg);
        }
    }

    @Override
    public String getName()
    {
        return "accept";
    }

    @Override
    public String getDescription()
    {
        return "accept invitation to a shared folder. create the folder in the root folder";
    }

    @Override
    public String getOptsSyntax()
    {
        return "INVITATION_CODE...";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

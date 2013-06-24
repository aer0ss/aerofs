package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdPwd implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws ExBadArgs
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        s.out().println(s.d().getPwd_().toStringFormal());
    }

    @Override
    public String getName()
    {
        return "pwd";
    }

    @Override
    public String getDescription()
    {
        return "print the working directory";
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
    public boolean isHidden()
    {
        return false;
    }
}

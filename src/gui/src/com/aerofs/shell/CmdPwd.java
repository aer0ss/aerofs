package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;

public class CmdPwd extends AbstractShellCommand<ShProgram>
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
}

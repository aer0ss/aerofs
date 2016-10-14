package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.SystemUtil.ExitCode;
import org.apache.commons.cli.CommandLine;

// templated because ShProgram cannot deal properly with commands with different templated types
public class CmdExit<S> extends AbstractShellCommand<S>
{
    @Override
    public void execute(ShellCommandRunner<S> s, CommandLine cl)
            throws ExBadArgs
    {
        exitShell();
    }

    static void exitShell()
    {
        ExitCode.NORMAL_EXIT.exit();
    }

    @Override
    public String getName()
    {
        return "exit";
    }

    @Override
    public String getDescription()
    {
        return "exit the process";
    }
}

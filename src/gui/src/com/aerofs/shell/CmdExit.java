package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
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
        System.exit(0);
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

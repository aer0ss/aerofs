package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

// templated because ShProgram cannot deal properly with commands with different templated types
public class CmdExit<S> implements IShellCommand<S>
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

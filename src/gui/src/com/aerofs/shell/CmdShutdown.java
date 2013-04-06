package com.aerofs.shell;

import com.aerofs.labeling.L;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdShutdown implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        s.d().getRitualClient_().shutdown();
        CmdExit.exitShell();
    }

    @Override
    public String getName()
    {
        return "shutdown";
    }

    @Override
    public String getDescription()
    {
        return "shutdown " + L.product() + " and exit the current process";
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

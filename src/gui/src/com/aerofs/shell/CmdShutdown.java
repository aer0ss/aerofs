package com.aerofs.shell;

import com.aerofs.labeling.L;
import org.apache.commons.cli.CommandLine;

public class CmdShutdown extends AbstractShellCommand<ShProgram>
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
}

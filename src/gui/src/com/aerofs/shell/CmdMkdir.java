package com.aerofs.shell;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdMkdir implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        for (String arg : cl.getArgs()) {
            PBPath path = s.d().buildPath_(arg);
            s.d().getRitualClient_().createObject(path, true);
        }
    }

    @Override
    public String getName()
    {
        return "mkdir";
    }

    @Override
    public String getDescription()
    {
        return "create directories";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH]...";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

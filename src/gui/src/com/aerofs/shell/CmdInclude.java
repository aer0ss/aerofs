package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdInclude implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        PBPath path = s.d().buildPath_(cl.getArgs()[0]);
        s.d().getRitualClient_().includeFolder(path);
    }

    @Override
    public String getName()
    {
        return "include";
    }

    @Override
    public String getDescription()
    {
        return "include a previously excluded folder";
    }

    @Override
    public String getOptsSyntax()
    {
        return "FOLDER";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdMv implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 2) throw new ExBadArgs();

        PBPath to = s.d().buildPath_(cl.getArgs()[1]);
        if (new Path(to).isEmpty()) throw new ExBadArgs("incorrect destination path");

        s.d().getRitualClient_().moveObject(s.d().buildPath_(cl.getArgs()[0]), to);
    }

    @Override
    public String getName()
    {
        return "mv";
    }

    @Override
    public String getDescription()
    {
        return "rename a file";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[FROM] [TO]";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

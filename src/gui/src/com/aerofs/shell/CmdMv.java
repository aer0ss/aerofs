package com.aerofs.shell;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.fsi.AeroFile;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.List;

public class CmdMv implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 2) throw new ExBadArgs();

        List<String> to = s.d().buildPathElemList_(cl.getArgs()[1]);
        if (to.size() < 1) throw new ExBadArgs("incorrect destination path");
        String toName = to.remove(to.size() - 1);

        new AeroFile(s.d().getFSIClient_(), s.d().buildPath_(cl.getArgs()[0]))
                .move_(ShProgram.buildPath_(to), toName);
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

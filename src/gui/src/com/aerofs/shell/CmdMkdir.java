package com.aerofs.shell;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.fsi.AeroFile;
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
            AeroFile f = new AeroFile(s.d().getFSIClient_(), s.d().buildPath_(arg));
            f.mkdir_(true);
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

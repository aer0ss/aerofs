package com.aerofs.shell;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.Collections;
import java.util.List;

public class CmdDelUser implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "deluser";
    }

    @Override
    public String getDescription()
    {
        return "stop sharing a folder with a specified user";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH] [USER]";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgList().size() != 2) throw new ExBadArgs();

        PBPath path = s.d().buildPath_(cl.getArgs()[0]);
        List<String> subjects = Collections.singletonList(cl.getArgs()[1]);
        s.d().getRitualClient_().deleteACL(Cfg.user().toString(), path, subjects);
    }
}

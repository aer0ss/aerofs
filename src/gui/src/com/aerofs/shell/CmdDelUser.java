package com.aerofs.shell;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.LinkedList;
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

        List<String> subjects = new LinkedList<String>();
        subjects.add(cl.getArgs()[1]);
        s.d().getRitualClient_().deleteACL(Cfg.user(), s.d().buildPath_(cl.getArgs()[0]), subjects);
    }
}

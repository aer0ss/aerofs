package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.Path;
import org.apache.commons.cli.CommandLine;

public class CmdDelUser extends AbstractShellCommand<ShProgram>
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
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgList().size() != 2) throw new ExBadArgs();

        Path path = s.d().buildPath_(cl.getArgs()[0]);
        s.d().getRitualClient_().deleteACL(path.toPB(), cl.getArgs()[1]);
    }
}

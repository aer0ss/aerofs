package com.aerofs.shell;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.fsi.AeroFile;
import com.aerofs.lib.ex.ExUIMessage;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.List;

public class CmdCd implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        List<String> path = s.d().buildPathElemList_(cl.getArgs()[0]);

        if (!path.isEmpty() && !new AeroFile(s.d().getFSIClient_(), ShProgram.buildPath_(path)).
                isDir_()) {
            throw new ExUIMessage("not a directory");
        }

        s.d().setPwdElements_(path);
    }

    @Override
    public String getName()
    {
        return "cd";
    }

    @Override
    public String getDescription()
    {
        return "enter a folder";
    }

    @Override
    public String getOptsSyntax()
    {
        return "PATH";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

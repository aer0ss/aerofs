package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBPath;
import org.apache.commons.cli.CommandLine;

import java.util.List;

public class CmdExcluded extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        List<PBPath> paths = s.d().getRitualClient_().listExcludedFolders().getPathList();
        for (PBPath path : paths) s.out().println(Path.fromPB(path));
    }

    @Override
    public String getName()
    {
        return "excluded";
    }

    @Override
    public String getDescription()
    {
        return "list excluded folders";
    }
}

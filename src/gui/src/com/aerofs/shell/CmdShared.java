package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Ritual.PBSharedFolder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.List;

public class CmdShared implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        List<PBSharedFolder> sharedFolders =
                s.d().getRitualClient_().listSharedFolders().getSharedFolderList();
        for (PBSharedFolder sf : sharedFolders) s.out().println(Path.fromPB(sf.getPath()));
    }

    @Override
    public String getName()
    {
        return "shared";
    }

    @Override
    public String getDescription()
    {
        return "list shared folders";
    }

    @Override
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

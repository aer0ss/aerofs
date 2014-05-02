package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.PBSharedFolder;
import org.apache.commons.cli.CommandLine;

import java.util.List;

public class CmdShared extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        List<PBSharedFolder> sharedFolders =
                s.d().getRitualClient_().listSharedFolders().getSharedFolderList();
        for (PBSharedFolder sf : sharedFolders) {
            Path path = Path.fromPB(sf.getPath());
            // Avoid printing SID whenever possible
            s.out().println(path.sid().equals(s.d().getPwd_().sid())
                ? path.toStringRelative()
                : path.toStringFormal());
        }
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
}

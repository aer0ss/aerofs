package com.aerofs.shell;

import com.aerofs.base.id.SID;
import org.apache.commons.cli.CommandLine;

import com.aerofs.base.ex.ExBadArgs;

public class CmdAccept extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        for (String arg : cl.getArgs()) {
            s.d().getRitualClient_().joinSharedFolder(new SID(arg, 0, arg.length()).toPB());
        }
    }

    @Override
    public String getName()
    {
        return "accept";
    }

    @Override
    public String getDescription()
    {
        return "accept invitation to a shared folder. create the folder in the root folder." +
                "see the \"invitations\" command for a list of pending invitations";
    }

    @Override
    public String getOptsSyntax()
    {
        return "SHARED_FOLDER_IDENTIFIER...";
    }
}

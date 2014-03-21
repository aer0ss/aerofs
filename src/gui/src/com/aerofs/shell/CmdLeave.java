/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;

public class CmdLeave extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        for (String arg : cl.getArgs()) {
            s.d().getRitualClient_().leaveSharedFolder(s.d().buildPBPath_(arg));
        }
    }

    @Override
    public String getName()
    {
        return "leave";
    }

    @Override
    public String getDescription()
    {
        return "Leave a shared folder. The content of the shared folder will be deleted." +
                "You can re-join the shared folder at any time through the \"accept\" command.";
    }

    @Override
    public String getOptsSyntax()
    {
        return "PATH...";
    }
}

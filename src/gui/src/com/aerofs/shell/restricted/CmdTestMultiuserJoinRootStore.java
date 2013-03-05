/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell.restricted;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.shell.IShellCommand;
import com.aerofs.shell.ShProgram;
import com.aerofs.shell.ShellCommandRunner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;


public class CmdTestMultiuserJoinRootStore implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        for (String arg : cl.getArgs()) {
            s.d().getRitualClient_().testMultiuserJoinRootStore(arg);
        }
    }

    @Override
    public String getName()
    {
        return "joinroot";
    }

    @Override
    public String getDescription()
    {
        return "join root stores";
    }

    @Override
    public String getOptsSyntax()
    {
        return "USER_ID...";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

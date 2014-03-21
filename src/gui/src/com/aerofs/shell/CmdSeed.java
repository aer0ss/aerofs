/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Ritual.CreateSeedFileReply;
import org.apache.commons.cli.CommandLine;

public class CmdSeed extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "seed";
    }

    @Override
    public String getDescription()
    {
        return "Create a seed file";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        CreateSeedFileReply reply = s.d().getRitualClient_().createSeedFile(s.d().getPwd_().sid().toPB());
        s.out().println("seed file: " + reply.getPath());
    }

    @Override
    public boolean isHidden()
    {
        // TODO(huguesb): unhide the command when seed files are exposed to users
        return true;
    }
}

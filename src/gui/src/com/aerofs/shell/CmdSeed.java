/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Ritual.CreateSeedFileReply;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdSeed implements IShellCommand<ShProgram>
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
    public String getOptsSyntax()
    {
        return "";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length != 0) throw new ExBadArgs();

        CreateSeedFileReply reply = s.d().getRitualClient_().createSeedFile();
        s.out().println("seed file: " + reply.getPath());
    }
}

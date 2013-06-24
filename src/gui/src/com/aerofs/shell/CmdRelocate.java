/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.base.ex.ExBadArgs;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdRelocate implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "relocate";
    }

    @Override
    public String getDescription()
    {
        return "moves AeroFS folder to a new location. AeroFS folder will be created if not " +
                "present";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH]";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 1) {
            throw new ExBadArgs();
        }

        s.d().getRitualClient_().relocate(RootAnchorUtil.adjustRootAnchor(cl.getArgs()[0], null),
                null);
    }

    @Override
    public boolean isHidden()
    {
        return false;
    }
}

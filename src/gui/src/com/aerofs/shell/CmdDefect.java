/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.defects.PriorityDefect.Factory;
import com.aerofs.labeling.L;
import com.aerofs.ritual.RitualClientProvider;
import org.apache.commons.cli.CommandLine;

public class CmdDefect extends AbstractShellCommand<ShProgram>
{
    private final Factory _defectFactory;

    public CmdDefect(RitualClientProvider ritualProvider)
    {
        _defectFactory = new Factory(ritualProvider);
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        StringBuilder sb = new StringBuilder("(via shell): ");
        if (cl.getArgs().length == 0) {
            throw new ExBadArgs("please provide a brief description of the problem");
        }

        for (String arg : cl.getArgs()) {
            sb.append(arg);
            sb.append(" ");
        }

        _defectFactory.newPriorityDefect()
                .setMessage(sb.toString())
                .setSendFilenames(true)
                .sendSyncIgnoreErrors();
    }

    @Override
    public String getName()
    {
        return "report";
    }

    @Override
    public String getDescription()
    {
        return "report an issue to the " + L.product() + " team." +
                " Type \"report\" followed by a short description.";
    }

    @Override
    public String getOptsSyntax()
    {
        return "DESCRIPTION";
    }
}

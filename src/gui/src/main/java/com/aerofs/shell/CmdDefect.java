/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.defects.UIPriorityDefect.Factory;
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
        if (cl.getArgs().length != 2) {
            throw new ExBadArgs("please provide a brief description of the problem");
        }

        String subject = cl.getArgList().get(0).toString();
        String message = cl.getArgList().get(1).toString();

        _defectFactory.newPriorityDefect()
                .setSubject(subject)
                .setMessage(message)
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
        return "SUBJECT MESSAGE";
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.text.SimpleDateFormat;

public class CmdActivities extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "activities";
    }

    @Override
    public String getDescription()
    {
        return "list recent activities";
    }

    @Override
    public Options getOpts()
    {
        return new Options()
                .addOption("c", "count", true, "maximum number of recent activities to display")
                .addOption("b", "brief", false, "display activities in brief");
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        int count;
        try {
            count = Integer.parseInt(cl.getOptionValue('c', "5"));
        } catch (NumberFormatException e) {
            throw new ExBadArgs("bad number format");
        }

        GetActivitiesReply reply = s.d().getRitualClient_().getActivities(cl.hasOption("b"), count, null);

        for (PBActivity activity : reply.getActivityList()) {
            String strTime = new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(activity.getTime());

            s.out().println(strTime + " " + activity.getMessage());
        }
    }
}

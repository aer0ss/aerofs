package com.aerofs.shell;

import com.aerofs.labeling.L;
import org.apache.commons.cli.CommandLine;

import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;

public class CmdVersion extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        s.out().println(L.product() + " " + Cfg.ver());
        s.out().println("Copyright " + S.COPYRIGHT);
    }

    @Override
    public String getName()
    {
        return "version";
    }

    @Override
    public String getDescription()
    {
        return "display " + L.product() + " version infomation";
    }
}

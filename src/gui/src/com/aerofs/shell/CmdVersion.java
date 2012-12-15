package com.aerofs.shell;

import com.aerofs.labeling.L;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;

public class CmdVersion implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        s.out().println(L.PRODUCT + " " + Cfg.ver());
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
        return "display " + L.PRODUCT + " version infomation";
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
}

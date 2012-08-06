package com.aerofs.shell;

import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.fsi.AeroFile;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdRm implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        for (String arg : cl.getArgs()) {
            AeroFile f = new AeroFile(s.d().getFSIClient_(), s.d().buildPath_(arg));
            if (f.isDir_()) {
                if (cl.hasOption('r')) {
                    f.delete_();
                } else {
                    throw new ExNotFile();
                }
            } else {
                f.delete_();
            }
        }
    }

    @Override
    public String getName()
    {
        return "rm";
    }

    @Override
    public String getDescription()
    {
        return "delete files";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH]...";
    }

    @Override
    public Options getOpts()
    {
        return new Options().addOption("r", "recursive", false, "recursively delete directories");
    }
}

package com.aerofs.shell;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.google.protobuf.ByteString;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdRm implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length == 0) throw new ExBadArgs();

        if (cl.hasOption('h')) {
            if (cl.hasOption('r')) {
                for (String arg : cl.getArgs()) {
                    PBPath path = s.d().buildPath_(arg);
                    s.d().getRitualClient_().deleteRevision(path, null);
                }
            } else {
                if (cl.getArgs().length != 2) throw new ExBadArgs();
                PBPath path = s.d().buildPath_(cl.getArgs()[0]);
                ByteString index = ByteString.copyFromUtf8(cl.getArgs()[1]);
                s.d().getRitualClient_().deleteRevision(path, index);
            }
        } else {
            for (String arg : cl.getArgs()) {
                PBPath path = s.d().buildPath_(arg);

            PBObjectAttributes attr = s.d().getRitualClient_()
                    .getObjectAttributes(Cfg.user().getString(), path)
                    .getObjectAttributes();

            if (!(attr.getType() == Type.FILE || cl.hasOption('r'))) throw new ExNotFile();

                s.d().getRitualClient_().deleteObject(path);
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
        return "[[PATH]...|PATH REVINDEX]";
    }

    @Override
    public Options getOpts()
    {
        return new Options()
                .addOption("r", "recursive", false, "recursively delete directories")
                .addOption("h", "history", false, "delete old version(s)");
    }
}

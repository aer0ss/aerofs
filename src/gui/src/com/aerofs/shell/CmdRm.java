package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.google.protobuf.ByteString;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdRm extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        final String[] args = cl.getArgs();
        if (args.length == 0) throw new ExBadArgs();

        if (cl.hasOption('h')) {
            if (cl.hasOption('r')) {
                for (String arg : args) {
                    s.d().getRitualClient_().deleteRevision(s.d().buildPBPath_(arg), null);
                }
            } else {
                if (args.length != 2) throw new ExBadArgs();
                s.d().getRitualClient_().deleteRevision(
                        s.d().buildPBPath_(args[0]),
                        ByteString.copyFromUtf8(args[1]));
            }
        } else {
            for (String arg : args) {
                PBPath path = s.d().buildPBPath_(arg);

                PBObjectAttributes attr = s.d().getRitualClient_()
                        .getObjectAttributes(path)
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

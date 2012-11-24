package com.aerofs.shell;

import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.List;

public class CmdCd implements IShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        List<String> pathElements = s.d().buildPathElemList_(cl.getArgs()[0]);
        Path path = new Path(pathElements);

        if (!path.isEmpty()) {
            PBObjectAttributes attr = s.d().getRitualClient_()
                    .getObjectAttributes(Cfg.user().toString(), path.toPB()).getObjectAttributes();
            if (attr.getType() != Type.FOLDER && attr.getType() != Type.SHARED_FOLDER) {
                throw new ExUIMessage("not a directory");
            }
        }

        s.d().setPwdElements_(pathElements);
    }

    @Override
    public String getName()
    {
        return "cd";
    }

    @Override
    public String getDescription()
    {
        return "enter a folder";
    }

    @Override
    public String getOptsSyntax()
    {
        return "PATH";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }
}

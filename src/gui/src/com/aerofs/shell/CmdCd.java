package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import org.apache.commons.cli.CommandLine;

public class CmdCd extends AbstractShellCommand<ShProgram>
{
    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl) throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        Path path = s.d().buildPath_(cl.getArgs()[0]);

        if (!path.isEmpty()) {
            PBObjectAttributes attr = s.d().getRitualClient_()
                    .getObjectAttributes(path.toPB()).getObjectAttributes();
            if (attr.getType() != Type.FOLDER && attr.getType() != Type.SHARED_FOLDER) {
                throw new ExUIMessage("not a directory");
            }
        }

        s.d().setPwd_(path);
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
}

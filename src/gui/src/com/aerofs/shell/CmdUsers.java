package com.aerofs.shell;

import java.util.List;

import com.aerofs.lib.Path;
import com.aerofs.base.acl.Role;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Ritual.GetACLReply;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdUsers implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "users";
    }

    @Override
    public String getDescription()
    {
        return "list users and their roles for a shared folder";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH]";
    }

    @Override
    public Options getOpts()
    {
        return ShellCommandRunner.EMPTY_OPTS;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgs().length != 1) throw new ExBadArgs();

        List<String> path = s.d().buildPathElemList_(cl.getArgs()[0]);

        GetACLReply reply = s.d().getRitualClient_().getACL(Cfg.user().getString(),
                new Path(path).toPB());

        for (int i = 0; i < reply.getSubjectRoleCount(); i++) {
            PBSubjectRolePair pair = reply.getSubjectRole(i);
            s.out().println(Role.fromPB(pair.getRole()).name() + '\t' + pair.getSubject());
        }
    }
}

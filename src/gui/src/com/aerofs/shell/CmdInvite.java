package com.aerofs.shell;

import java.util.Collections;

import com.aerofs.gui.sharing.CompInviteUsers;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.UserID;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Common.PBPath;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdInvite implements IShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "invite";
    }

    @Override
    public String getDescription()
    {
        return "share a folder with a user with a specified role, and send an invitation email";
    }

    @Override
    public String getOptsSyntax()
    {
        return "[PATH] [USER]";
    }

    @Override
    public Options getOpts()
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Role role : Role.values()) {
            if (!first) sb.append(" or ");
            first = false;
            sb.append(role.getDescription());
        }

        return new Options().addOption("r", "role", true, "specify the role (" +
                sb.toString() + "). Default is " + Role.EDITOR.getDescription());
    }

    @Override
    public boolean isHidden()
    {
        return false;
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgList().size() != 2) throw new ExBadArgs();

        String role = cl.getOptionValue('r', Role.EDITOR.getDescription());

        PBPath path = s.d().buildPBPath_(cl.getArgs()[0]);
        SubjectRolePair srp = new SubjectRolePair(UserID.fromExternal(cl.getArgs()[1]),
                Role.fromString(role));

        String name = path.getElemCount() == 0 ? "unkown folder" :
                path.getElem(path.getElemCount() - 1);
        String note = CompInviteUsers.getDefaultInvitationNote(name, Cfg.user().getString());

        RitualBlockingClient ritual = s.d().getRitualClient_();
        ritual.shareFolder(path, Collections.singletonList(srp.toPB()), note, false);
    }
}

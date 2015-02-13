package com.aerofs.shell;

import java.util.Collection;
import java.util.Collections;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.gui.sharing.invitee.CompInviteUsers;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.ids.UserID;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Common.PBPath;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang.WordUtils;

public class CmdInvite extends AbstractShellCommand<ShProgram>
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

    private static Permissions fromString(String name) throws ExBadArgs
    {
        Permissions permissions = Permissions.fromRoleName(WordUtils.capitalize(name));
        if (permissions == null) throw new ExBadArgs("Unknown role: " + name);
        return permissions;
    }

    public String prettyJoin(Collection<String> elems, String conjunction)
    {
        StringBuilder bd = new StringBuilder();
        int i = 0;
        for (String e : elems) {
            if (bd.length() > 0) {
                if (++i == elems.size()) {
                    bd.append(' ').append(conjunction).append(' ');
                } else {
                    bd.append(", ");
                }
            }
            bd.append(e);
        }
        return bd.toString();
    }

    @Override
    public Options getOpts()
    {

        return new Options().addOption("r", "role", true, "specify the role ("
                + prettyJoin(Permissions.ROLE_NAMES.values(), "or") + "). Default is "
                + Permissions.EDITOR.roleName() + ".");
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        if (cl.getArgList().size() != 2) throw new ExBadArgs();

        String role = cl.getOptionValue('r', Permissions.EDITOR.roleName());

        PBPath path = s.d().buildPBPath_(cl.getArgs()[0]);
        SubjectPermissions srp = new SubjectPermissions(
                SubjectPermissions.getUserIDFromString(cl.getArgs()[1]),
                fromString(role));

        String name = path.getElemCount() == 0 ? "unknown folder" :
                path.getElem(path.getElemCount() - 1);
        String note = CompInviteUsers.getDefaultInvitationNote(name, Cfg.user().getString());

        RitualBlockingClient ritual = s.d().getRitualClient_();
        ritual.shareFolder(path, Collections.singletonList(srp.toPB()), note, false);
    }
}

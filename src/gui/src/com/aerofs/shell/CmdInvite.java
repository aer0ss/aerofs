package com.aerofs.shell;

import java.util.Collections;

import com.aerofs.gui.sharing.CompInviteUsers;
import com.aerofs.lib.Role;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.proto.Common.PBPath;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class CmdInvite implements IShellCommand<ShProgram>
{
    private final String _optionString;

    public CmdInvite()
    {
        _optionString = buildOptionString_();
    }

    private static String buildOptionString_()
    {
        String options = "[PATH] [USER] {";
        boolean first = true;
        for (Role role : Role.values()) {
            if (!first) options += ", ";
            first = false;
            options += role.toString();
        }
        options += "}";

        return options;
    }

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
        return _optionString;
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
        if (cl.getArgList().size() != 3) throw new ExBadArgs();

        PBPath path = s.d().buildPath_(cl.getArgs()[0]);
        SubjectRolePair srp = new SubjectRolePair(cl.getArgs()[1],
                Role.fromString(cl.getArgs()[2]));

        RitualBlockingClient ritual = s.d().getRitualClient_();
        SID sid = new SID(ritual.shareFolder(Cfg.user(), path,
                Collections.singletonList(srp.toPB())) .getShareId());

        String name = path.getElemCount() == 0 ? "unkown folder" :
                path.getElem(path.getElemCount() - 1);
        String note = CompInviteUsers.getDefaultInvitationNote(name, Cfg.user());
        s.d().getSPClient_().shareFolder(name, sid.toPB(), Collections.singletonList(srp._subject),
                note);
    }
}

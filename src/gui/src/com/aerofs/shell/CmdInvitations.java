/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.shell;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.SID;
import com.aerofs.proto.Common.PBFolderInvitation;
import org.apache.commons.cli.CommandLine;

import java.util.List;

public class CmdInvitations extends AbstractShellCommand<ShProgram>
{
    @Override
    public String getName()
    {
        return "invitations";
    }

    @Override
    public String getDescription()
    {
        return "list pending shared folder invitations. use the \"accept\" command to accept them";
    }

    @Override
    public void execute(ShellCommandRunner<ShProgram> s, CommandLine cl)
            throws Exception
    {
        List<PBFolderInvitation> invitations = s.d().getRitualClient_()
                .listSharedFolderInvitations().getInvitationList();

        if (invitations.isEmpty()) {
            s.out().println("No pending invitations");
            return;
        }

        s.out().println("Shared Folder Identifier         Shared Folder Name    Sharer");
        s.out().println("-------------------------------------------------------------");
        for (PBFolderInvitation inv : invitations) {
            s.out().println(new SID(BaseUtil.fromPB(inv.getShareId())).toStringFormal() + " " +
                    inv.getFolderName() + " " + inv.getSharer() + " ");
        }
    }
}

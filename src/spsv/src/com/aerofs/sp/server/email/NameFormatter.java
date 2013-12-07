/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.FullName;
import com.aerofs.sp.server.lib.user.User;

import java.sql.SQLException;

/**
 * A utility class that formats user names and email addresses nicely. When the user's last or
 * first name is empty, both nameOnly() and nameAndEmail() return the email address. Otherwise,
 * nameOnly() returns the full name, and nameAndEmail() return "Full Name <email>".
 */
class NameFormatter
{
    private String _inviterName;
    private String _inviterLongName;

    /**
     * N.B. this method must be called within an SP database transaction
     */
    public NameFormatter(User user)
            throws SQLException, ExNotFound
    {
        if (user.id().isTeamServerID()) {
            _inviterName = _inviterLongName = "Organization Admin";
        } else {
            FullName inviterFullName = user.getFullName();
            if (inviterFullName.isFirstOrLastNameEmpty()) {
                _inviterName = _inviterLongName = user.id().getString();
            } else {
                _inviterName = inviterFullName.getString();
                _inviterLongName = _inviterName + " <" + user.id().getString() + ">";
            }
        }
    }

    public String nameOnly()
    {
        return _inviterName;
    }

    public String nameAndEmail()
    {
        return _inviterLongName;
    }
}

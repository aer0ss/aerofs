/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;

public class OrganizationInvitation
{
    private final OrganizationInvitationDatabase _db;
    private final User.Factory _factUser;
    private final Organization.Factory _factOrg;

    private final UserID _invitee;
    private final OrganizationID _org;

    public static class Factory
    {
        private OrganizationInvitationDatabase _db;
        private User.Factory _factUser;
        private Organization.Factory _factOrg;

        @Inject
        public void inject(OrganizationInvitationDatabase db, User.Factory factUser,
                Organization.Factory factOrg)
        {
            _db = db;
            _factUser = factUser;
            _factOrg = factOrg;
        }

        public OrganizationInvitation create(@Nonnull UserID invitee, @Nonnull OrganizationID org)
        {
            return new OrganizationInvitation(_db, _factUser, _factOrg, invitee, org);
        }

        public OrganizationInvitation save(@Nonnull UserID inviter, @Nonnull UserID invitee,
                @Nonnull OrganizationID org)
                throws SQLException
        {
            _db.insert(inviter, invitee, org);
            return new OrganizationInvitation(_db, _factUser, _factOrg, invitee, org);
        }
    }

    private OrganizationInvitation(OrganizationInvitationDatabase db, User.Factory factUser,
            Organization.Factory factOrg, UserID invitee, OrganizationID org)
    {
        _db = db;
        _factUser = factUser;
        _factOrg = factOrg;

        _invitee = invitee;
        _org = org;
    }

    public User getInviter()
            throws SQLException, ExNotFound
    {
        return _factUser.create(_db.getInviter(_invitee, _org));
    }

    public Organization getOrganization()
            throws SQLException
    {
        return _factOrg.create(_org);
    }

    /**
     * Return true if the organization invite exists.
     */
    public boolean exists()
        throws SQLException
    {
        return _db.hasInvite(_invitee, _org);
    }

    /**
     * Delete an organization invitation.
     * @throws ExNotFound if the organization invitation does not exist.
     */
    public void delete()
            throws ExNotFound, SQLException
    {
        if (!exists()) {
            throw new ExNotFound();
        }

        _db.delete(_invitee, _org);
    }
}
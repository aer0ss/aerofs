/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.base.ex.ExNotFound;
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

    private final User _invitee;
    private final Organization _org;

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

        /**
         * TODO (WW) use User and Organization objects rather than ID objects
         */
        public OrganizationInvitation create(@Nonnull UserID invitee, @Nonnull OrganizationID orgID)
        {
            return new OrganizationInvitation(_db, _factUser, invitee, _factOrg.create(orgID));
        }

        /**
         * TODO (WW) use User and Organization objects rather than ID objects
         */
        public OrganizationInvitation save(@Nonnull UserID inviter, @Nonnull UserID invitee,
                @Nonnull OrganizationID orgID)
                throws SQLException
        {
            _db.insert(inviter, invitee, orgID);
            return create(invitee, orgID);
        }
    }

    private OrganizationInvitation(OrganizationInvitationDatabase db, User.Factory factUser,
            UserID invitee, Organization org)
    {
        _db = db;
        _factUser = factUser;

        _invitee = _factUser.create(invitee);
        _org = org;
    }

    public User getInviter()
            throws SQLException, ExNotFound
    {
        return _factUser.create(_db.getInviter(_invitee.id(), _org.id()));
    }

    public User getInvitee()
    {
        return _invitee;
    }

    public Organization getOrganization()
            throws SQLException
    {
        return _org;
    }

    /**
     * Return true if the organization invite exists.
     */
    public boolean exists()
        throws SQLException
    {
        return _db.hasInvite(_invitee.id(), _org.id());
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

        _db.delete(_invitee.id(), _org.id());
    }
}
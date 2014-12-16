/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.sp.server.lib.organization.OrganizationInvitationDatabase.GetBySignUpCodeResult;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;

/**
 * TODO (WW) index invitations with invitation code, similar to signUp code?
 */
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

        public OrganizationInvitation create(@Nonnull User invitee, @Nonnull Organization org)
        {
            return new OrganizationInvitation(_db, _factUser, invitee, org);
        }

        /**
         * @param signUpCode an optional signUp code associated with the organization invitation.
         *  When the invited user signs up with this signup code the system will automatically
         *  accept the organization invitation on behalf of the user.
         */
        public OrganizationInvitation save(@Nonnull User inviter, @Nonnull User invitee,
                @Nonnull Organization org, @Nullable String signUpCode)
                throws SQLException
        {
            _db.insert(inviter.id(), invitee.id(), org.id(), signUpCode);
            return create(invitee, org);
        }

        public @Nullable OrganizationInvitation getBySignUpCodeNullable(String signUpCode)
                throws SQLException
        {
            GetBySignUpCodeResult res = _db.getBySignUpCodeNullable(signUpCode);
            if (res == null) return null;
            else return create(_factUser.create(res._userID), _factOrg.create(res._orgID));
        }
    }

    private OrganizationInvitation(OrganizationInvitationDatabase db, User.Factory factUser,
            User invitee, Organization org)
    {
        _db = db;
        _factUser = factUser;
        _invitee = invitee;
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
        _db.delete(_invitee.id(), _org.id());
    }

    public String getCode()
            throws SQLException, ExNotFound
    {
        return _db.getCode(_invitee.id(), _org.id());
    }
}
package com.aerofs.sp.server.user;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase.UserInfo;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * This class will grow into a wrapper for DB queries regarding users
 */
public class UserManagement
{
    // To avoid DoS attacks, do not permit listUsers queries to exceed 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    private final SPDatabase _db;
    private final IUserSearchDatabase _usdb;
    private final InvitationEmailer.Factory _emailerFactory;
    private final PasswordResetEmailer _passwordResetEmailer;

    private static final Logger l = Util.l(UserManagement.class);

    public UserManagement(SPDatabase db, IUserSearchDatabase usdb,
            InvitationEmailer.Factory emailerFactory, PasswordResetEmailer passwordResetEmailer)
    {
        _db = db;
        _usdb = usdb;
        _emailerFactory = emailerFactory;
        _passwordResetEmailer = passwordResetEmailer;
    }

    public static class UserListAndQueryCount
    {
        public final List<UserInfo> _uis;
        public final int _count;

        public UserListAndQueryCount(List<UserInfo> uis, int count)
        {
            _uis = uis;
            _count = count;
        }
    }

    /**
     * Query the User identified by userId from the db, never returning null
     * @throws ExNotFound if the specified userId was not found in the db
     */
    public @Nonnull User getUser(@Nonnull UserID userId)
            throws ExNotFound, IOException, SQLException
    {
        User u = getUserNullable(userId);
        if (u == null) throw new ExNotFound("email address not found (" + userId + ")");
        return u;
    }

    /**
     * Query the User identified by userId from the db, returning null if it doesn't exist
     */
    public @Nullable User getUserNullable(@Nonnull UserID userId)
            throws IOException, SQLException
    {
        return _db.getUserNullable(userId);
    }

    /**
     * This method performs business logic checks prior to sending invite email and wrap the
     * call to the emailer in a nice and clean Callable that should be used outside of the DB
     * transaction.
     *
     * @return a callable doing the actual email sending
     */
    public InvitationEmailer inviteOneUser(User inviter, UserID inviteeId, Organization inviteeOrg,
            @Nullable String folderName, @Nullable String note)
            throws Exception
    {
        assert inviteeId != null;

        // Check that the invitee doesn't exist already
        throwIfUserIdDoesNotExist(inviteeId);

        final String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);

        _db.addTargetedSignupCode(code, inviter.id(), inviteeId, inviteeOrg._id);

        _db.addEmailSubscription(inviteeId, SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        return _emailerFactory.createUserInvitation(inviter.id().toString(), inviteeId.toString(),
                inviter.getFirstName(), folderName, note, code);
    }

    public void throwIfUserIdDoesNotExist(UserID userId)
            throws SQLException, ExAlreadyExist
    {
        if (_db.getUserNullable(userId) != null) {
            throw new ExAlreadyExist("A user with this email address already exists");
        }
    }

    public void setAuthorizationLevel(UserID userId, AuthorizationLevel auth)
            throws SQLException
    {
        _db.setAuthorizationLevel(userId, auth);
    }

    public int totalUserCount(OrgID orgId) throws SQLException
    {
        return _usdb.listUsersCount(orgId);
    }

    public int totalUserCount(AuthorizationLevel authLevel, OrgID orgId)
            throws SQLException
    {
        return _usdb.listUsersWithAuthorizationCount(authLevel, orgId);
    }

    public UserListAndQueryCount listUsers(@Nullable String search, int maxResults,
            int offset, OrgID orgId)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";
        checkOffset(offset);
        checkMaxResults(maxResults);

        assert offset >= 0;

        List<UserInfo> users;
        int count;
        if (search.isEmpty()) {
            users = _usdb.listUsers(orgId, offset, maxResults);
            count = _usdb.listUsersCount(orgId);
        } else {
            assert !search.isEmpty();
            users = _usdb.searchUsers(orgId, offset, maxResults, search);
            count = _usdb.searchUsersCount(orgId, search);
        }
        return new UserListAndQueryCount(users, count);
    }

    /**
     * @param search Null or empty string when we want to find all the users.
     */
    public UserListAndQueryCount listUsersAuth(@Nullable String search,
            AuthorizationLevel authLevel, int maxResults,
            int offset, OrgID orgId)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";
        checkOffset(offset);
        checkMaxResults(maxResults);

        assert offset >= 0;

        List<UserInfo> users;
        int count;
        if (search.isEmpty()) {
            users = _usdb.listUsersWithAuthorization(orgId, offset, maxResults, authLevel);
            count = _usdb.listUsersWithAuthorizationCount(authLevel, orgId);
        }
        else {
            assert !search.isEmpty();
            users = _usdb.searchUsersWithAuthorization(
                        orgId, offset, maxResults, authLevel, search);
            count = _usdb.searchUsersWithAuthorizationCount(authLevel, orgId, search);
        }
        return new UserListAndQueryCount(users, count);
    }

    public void sendPasswordResetEmail(UserID userId)
            throws SQLException, IOException, MessagingException
    {
        User user;
        try {
             user = getUser(userId);
        } catch(ExNotFound e){
            // If we don't have a user, just do nothing
            l.info("Password reset requested for " + userId + " but user doesn't exist");
            return;
        }
        String token = Base62CodeGenerator.newRandomBase62String(SPParam
                .PASSWORD_RESET_TOKEN_LENGTH);
        _db.addPasswordResetToken(user.id(), token);
        _passwordResetEmailer.sendPasswordResetEmail(user.id(), token);
        l.info("Password Reset Email sent to " + user.id());
    }

    public void resetPassword(String passwordResetToken, ByteString newCredentials)
            throws SQLException, ExNotFound, IOException, MessagingException
    {
        UserID userId = _db.resolvePasswordResetToken(passwordResetToken);
        User user = getUser(userId);
        _db.updateUserCredentials(user.id(), SPParam.getShaedSP(newCredentials.toByteArray()));
        _db.deletePasswordResetToken(passwordResetToken);
        l.info("Reset " + userId + "'s Password");
        _passwordResetEmailer.sendPasswordResetConfirmation(user.id());
    }

    public void changePassword(UserID userId, ByteString old_credentials,
            ByteString new_credentials)
            throws ExNotFound, IOException, SQLException, ExNoPerm
    {
        User user = getUser(userId);
        _db.checkAndUpdateUserCredentials(user.id(),
                SPParam.getShaedSP(old_credentials.toByteArray()),
                SPParam.getShaedSP(new_credentials.toByteArray()));
        l.info(userId + "'s Password was successfully changed");
    }

    private static void checkOffset(int offset)
            throws ExBadArgs
    {
        if (offset < 0) throw new ExBadArgs("offset is negative");
    }

    private static void checkMaxResults(int maxResults)
            throws ExBadArgs
    {
        if (maxResults > ABSOLUTE_MAX_RESULTS) throw new ExBadArgs("maxResults is too big");
        else if (maxResults < 0) throw new ExBadArgs("maxResults is a negative number");
    }
}

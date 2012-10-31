package com.aerofs.sp.server.user;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.spsv.Base62CodeGenerator;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.InvitationCode.CodeType;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
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
        public final List<PBUser> users;
        public final int count;

        public UserListAndQueryCount(List<PBUser> u, int c)
        {
            users = u;
            count = c;
        }
    }

    /**
     * Query the User identified by userID from the db, never returning null
     * @throws ExNotFound if the specified userID was not found in the db
     */
    public @Nonnull User getUser(@Nonnull String userID)
            throws ExNotFound, IOException, SQLException
    {
        User u = getUserNullable(userID);
        if (u == null) throw new ExNotFound("email address not found (" + userID + ")");
        return u;
    }

    /**
     * Query the User identified by userID from the db, returning null if it doesn't exist
     */
    public @Nullable User getUserNullable(@Nonnull String userID)
            throws IOException, SQLException
    {
        return _db.getUser(userID);
    }

    /**
     * This method performs business logic checks prior to sending invite email and wrap the
     * call to the emailer in a nice and clean Callable that should be used outside of the DB
     * transaction.
     *
     * @return a callable doing the actual email sending
     */
    public InvitationEmailer inviteOneUser(User inviter, String inviteeId, Organization inviteeOrg,
            @Nullable String folderName, @Nullable String note)
            throws Exception
    {
        assert inviteeId != null;

        // TODO could change userId field in DB to be case-insensitive to avoid normalization
        final String normalizedId = User.normalizeUserId(inviteeId);

        // Check that the invitee doesn't exist already
        checkUserIdDoesNotExist(normalizedId);

        // USER-level inviters can only invite to an organization that matches the domain
        if (inviter._level.equals(AuthorizationLevel.USER)
                && !inviteeOrg.domainMatches(inviteeId)) {
            throw new ExNoPerm(inviter._id + " cannot invite + " + normalizedId
                    + " to " + inviteeOrg._id);
        }

        final String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);

        _db.addTargetedSignupCode(code, inviter._id, normalizedId, inviteeOrg._id);

        return _emailerFactory.createUserInvitation(inviter._id, normalizedId, inviter._firstName,
                        folderName, note, code);
    }

    public void checkUserIdDoesNotExist(String userId)
            throws SQLException, IOException, ExAlreadyExist
    {
        if (_db.getUser(userId) != null) {
            throw new ExAlreadyExist("A user with this email address already exists");
        }
    }

    public void setAuthorizationLevel(String userId, AuthorizationLevel auth)
            throws SQLException
    {
        _db.setAuthorizationLevel(userId, auth);
    }

    public int totalUserCount(String orgId) throws SQLException
    {
        return _usdb.listUsersCount(orgId);
    }

    public int totalUserCount(AuthorizationLevel authLevel, String orgId)
            throws SQLException
    {
        return _usdb.listUsersWithAuthorizationCount(authLevel, orgId);
    }

    public UserListAndQueryCount listUsers(@Nullable String search, int maxResults,
            int offset, String orgId)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";
        checkOffset(offset);
        checkMaxResults(maxResults);

        assert offset >= 0;

        List<PBUser> users;
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
            int offset, String orgId)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";
        checkOffset(offset);
        checkMaxResults(maxResults);

        assert offset >= 0;

        List<PBUser> users;
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

    public void sendPasswordResetEmail(String user_email)
            throws SQLException, ExNotFound, IOException, MessagingException
    {
        User user;
        try {
             user = getUser(user_email);
        } catch(ExNotFound e){
            // If we don't have a user, just do nothing
            l.info("Password reset requested for " + user_email + " but user doesn't exist");
            return;
        }
        String token = Base62CodeGenerator.newRandomBase62String(SPParam
                .PASSWORD_RESET_TOKEN_LENGTH);
        _db.addPasswordResetToken(user._id, token);
        _passwordResetEmailer.sendPasswordResetEmail(user_email,token);
        l.info("Password Reset Email sent to " + user_email);
    }

    public void resetPassword(String password_reset_token, ByteString new_credentials)
            throws SQLException, ExNotFound, IOException, MessagingException
    {
        String user_id = _db.resolvePasswordResetToken(password_reset_token);
        User user = getUser(user_id);
        _db.updateUserCredentials(user._id, SPParam.getShaedSP(new_credentials.toByteArray()));
        _db.deletePasswordResetToken(password_reset_token);
        l.info("Reset " + user_id + "'s Password");
        _passwordResetEmailer.sendPasswordResetConfirmation(user._id);
    }

    public void changePassword(String userId, ByteString old_credentials,
            ByteString new_credentials)
            throws ExNotFound, IOException, SQLException, ExNoPerm
    {
        User user = getUser(userId);
        _db.checkAndUpdateUserCredentials(user._id,
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

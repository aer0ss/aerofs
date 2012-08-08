package com.aerofs.sp.server.sp.user;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.spsv.Base62CodeGenerator;
import com.aerofs.proto.Sp.PBUser;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.servletlib.sp.SPDatabase;
import com.aerofs.servletlib.sp.SPParam;
import com.aerofs.servletlib.sp.user.User;
import com.aerofs.servletlib.sp.user.AuthorizationLevel;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * This class will grow into a wrapper for DB queries regarding users
 */
public class UserManagement
{
    private static final int DEFAULT_MAX_RESULTS = 100;
    // To avoid DoS attacks, do not permit listUsers queries to exceed 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    private final SPDatabase _db;
    private final PasswordResetEmailer _passwordResetEmailer;

    private static final Logger l = Util.l(UserManagement.class);

    public UserManagement(SPDatabase db, PasswordResetEmailer passwordResetEmailer)
    {
        _db = db;
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
    public User getUser(@Nonnull String userID)
            throws ExNotFound, IOException, SQLException
    {
        User u = _db.getUser(userID);
        if (u == null) throw new ExNotFound("email address not found (" + userID + ")");
        return u;
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

    public List<PBUser> listAllUsers(Integer maxResults, Integer offset, String orgId)
            throws SQLException
    {
        if (offset == null) offset = 0;
        assert offset >= 0;
        maxResults = sanitizeMaxResults(maxResults);

        return _db.listUsers(orgId, offset, maxResults, null);
    }

    public int totalUserCount(String orgId) throws SQLException
    {
        return _db.countUsers(orgId, null);
    }

    public UserListAndQueryCount listUsers(String search, Integer maxResults, Integer offset,
            String orgId)
            throws SQLException
    {
        if (offset == null) offset = 0;
        maxResults = sanitizeMaxResults(maxResults);

        assert offset >= 0;
        assert search != null && !search.isEmpty();

        List<PBUser> list =  _db.listUsers(orgId, offset, maxResults, search);
        int userCount = _db.countUsers(orgId, search);

        return new UserListAndQueryCount(list, userCount);
    }

    public void sendPasswordResetEmail(String user_email)
            throws Exception
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
            throws SQLException, ExNotFound, IOException
    {
        String user_id = _db.resolvePasswordResetToken(password_reset_token);
        User user = getUser(user_id);
        _db.updateUserCredentials(user._id, SPParam.getShaedSP(new_credentials.toByteArray()));
        _db.deletePasswordResetToken(password_reset_token);
        l.info("Reset " + user_id + "'s Password");
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

    private static Integer sanitizeMaxResults(Integer maxResults)
    {
        if (maxResults == null) return DEFAULT_MAX_RESULTS;
        else if (maxResults > ABSOLUTE_MAX_RESULTS) return ABSOLUTE_MAX_RESULTS;
        else if (maxResults < 0) return 0;

        else return maxResults;
    }
}

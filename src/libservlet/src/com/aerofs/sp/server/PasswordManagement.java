package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExCannotResetPassword;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.UserID;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.Factory;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;

/**
 * TODO (WW) OO-tify this class, and have an upper layer handle emailing logic.
 */
public class PasswordManagement
{
    private final SPDatabase _db;
    private final PasswordResetEmailer _passwordResetEmailer;
    private Authenticator _authenticator;
    private final User.Factory _factUser;

    private static final Logger l = Loggers.getLogger(PasswordManagement.class);

    @Inject
    public PasswordManagement(SPDatabase db, Factory factUser,
            PasswordResetEmailer passwordResetEmailer, Authenticator authenticator)
    {
        _db = db;
        _factUser = factUser;
        _passwordResetEmailer = passwordResetEmailer;
        _authenticator = authenticator;
    }

    /**
     * Generate a password reset token and email the user the reset token.
     */
    public void sendPasswordResetEmail(User user) throws Exception
    {
        if (!user.exists()) {
            // If we don't have a user, just do nothing
            l.info("Password reset requested for " + user + " but user doesn't exist");
            return;
        }

        if (!_authenticator.isLocallyManaged(user.id())) {
            l.info("Password reset requested for " + user + " but user has no local credential");
            throw new ExCannotResetPassword();
        }

        String token = Base62CodeGenerator.generate();
        _db.insertPasswordResetToken(user.id(), token);
        _passwordResetEmailer.sendPasswordResetEmail(user.id(), token);
        l.info("Password Reset Email sent to " + user.id());
    }

    /**
     * Reset the password given a password-reset token and new cleartext credentials.
     */
    public User resetPassword(String passwordResetToken, byte[] newCredentials)
            throws SQLException, ExNotFound, IOException, MessagingException,
            GeneralSecurityException, ExExternalServiceUnavailable, ExCannotResetPassword
    {
        UserID userId = _db.resolvePasswordResetToken(passwordResetToken);
        User user = getUserAndThrowIfNoPassword(userId);
        _db.updateUserCredentials(user.id(),
                LocalCredential.hashScrypted(
                        LocalCredential.deriveKeyForUser(userId, newCredentials)));
        _db.deletePasswordResetToken(passwordResetToken);

        l.info("Reset {}'s Password", userId.getString());
        _passwordResetEmailer.sendPasswordResetConfirmation(user.id());
        return user;
    }

    // FIXME: update to use server-side SCrypt; must be updated along with CmdPassword
    // TODO (WW) move it to the User class (jP: Why?)
    /**
     * Given a user and their current credentials, store a new credential.
     *
     * NOTE: for historical reasons, this expects a SCrypt'ed credential. That must change.
     */
    public User replacePassword(UserID userId, byte[] old_credentials, byte[] new_credentials)
            throws SQLException, ExNotFound, ExExternalServiceUnavailable, ExCannotResetPassword,
            ExNoPerm
    {
        User user = getUserAndThrowIfNoPassword(userId);

        _db.checkAndUpdateUserCredentials(user.id(),
                SPParam.getShaedSP(old_credentials),
                SPParam.getShaedSP(new_credentials));
        l.info("{}'s Password was successfully changed", userId.getString());
        return user;
    }

    /**
     * Revoke the users' password (make this account inaccessible until the password is reset)
     * and send a password reset token by email.
     *
     * This will throw an exception if the user does not exist, or does not have a local credential
     * (and related errors)
     */
    public User revokePassword(UserID userId)
            throws SQLException, ExCannotResetPassword, ExNotFound, ExExternalServiceUnavailable,
            IOException, MessagingException
    {
        User u = getUserAndThrowIfNoPassword(userId);
        String token = Base62CodeGenerator.generate();

        _db.updateUserCredentials(userId, new byte[0]);
        _db.insertPasswordResetToken(userId, token);

        _passwordResetEmailer.sendPasswordRevokeNotification(userId, token);
        l.info("Password revocation email sent to " + userId);

        return u;
    }

    /**
     * Set a user's credential.
     *
     * Authentication must occur outside this method - this does not take the existing
     * credential. This may be the result of an administrative action.
     *
     * This expects a plaintext credential.
     */
    public User setPassword(UserID userId, byte[] newCredentials)
            throws GeneralSecurityException, SQLException, ExNotFound, IOException,
            MessagingException, ExExternalServiceUnavailable, ExCannotResetPassword
    {
        User user = getUserAndThrowIfNoPassword(userId);
        _db.updateUserCredentials(user.id(),
                LocalCredential.hashScrypted(
                        LocalCredential.deriveKeyForUser(user.id(), newCredentials)));

        _passwordResetEmailer.sendPasswordChangeNotification(userId);
        l.info("Explicit password set for {}", userId);

        return user;
    }

    /**
     * Return a User object, only if the userId points to an existing account that has a
     * local credential.
     */
    private User getUserAndThrowIfNoPassword(UserID userId)
            throws SQLException, ExNotFound, ExCannotResetPassword, ExExternalServiceUnavailable
    {
        User u = _factUser.create(userId);
        u.throwIfNotFound();

        if (_authenticator.isLocallyManaged(userId)) return u;

        l.info("Password action requested for " + userId + " but user has no local credential");
        throw new ExCannotResetPassword();
    }
}

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExCannotResetPassword;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.User.Factory;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

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

    public PasswordManagement(SPDatabase db, Factory factUser,
            PasswordResetEmailer passwordResetEmailer, Authenticator authenticator)
    {
        _db = db;
        _factUser = factUser;
        _passwordResetEmailer = passwordResetEmailer;
        _authenticator = authenticator;
    }

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
    public User resetPassword(String passwordResetToken, ByteString newCredentials)
            throws SQLException, ExNotFound, IOException, MessagingException,
            GeneralSecurityException
    {
        UserID userId = _db.resolvePasswordResetToken(passwordResetToken);
        User user = _factUser.create(userId);
        user.throwIfNotFound();
        _db.updateUserCredentials(user.id(),
                LocalCredential.hashScrypted(
                        LocalCredential.deriveKeyForUser(userId, newCredentials.toByteArray())));
        _db.deletePasswordResetToken(passwordResetToken);
        l.info("Reset " + userId + "'s Password");
        _passwordResetEmailer.sendPasswordResetConfirmation(user.id());
        return user;
    }

    // FIXME: update to use server-side SCrypt; must be updated along with CmdPassword
    // TODO (WW) move it to the User class
    public User changePassword(UserID userId, ByteString old_credentials,
            ByteString new_credentials)
            throws ExNotFound, IOException, SQLException, ExNoPerm
    {
        User user = _factUser.create(userId);
        user.throwIfNotFound();
        _db.checkAndUpdateUserCredentials(user.id(),
                SPParam.getShaedSP(old_credentials.toByteArray()),
                SPParam.getShaedSP(new_credentials.toByteArray()));
        l.info(userId + "'s Password was successfully changed");
        return user;
    }
}

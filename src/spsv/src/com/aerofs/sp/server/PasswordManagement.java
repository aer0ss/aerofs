package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.base.ex.ExCannotResetPassword;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.UserFilter;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

/**
 * TODO (WW) OO-tify this class, and have an upper layer handle emailing logic.
 */
public class PasswordManagement
{
    private final SPDatabase _db;
    private final PasswordResetEmailer _passwordResetEmailer;
    private final UserFilter _userFilter;
    private final User.Factory _factUser;

    private static final Logger l = Loggers.getLogger(PasswordManagement.class);

    public PasswordManagement(SPDatabase db, User.Factory factUser,
            PasswordResetEmailer passwordResetEmailer,
            UserFilter userFilter)
    {
        _db = db;
        _factUser = factUser;
        _passwordResetEmailer = passwordResetEmailer;
        _userFilter = userFilter;
    }

    public void sendPasswordResetEmail(User user)
            throws SQLException, IOException, MessagingException, ExCannotResetPassword
    {
        if (!user.exists()) {
            // If we don't have a user, just do nothing
            l.info("Password reset requested for " + user + " but user doesn't exist");
            return;
        }

        if (!canResetPassword(user)) {
            l.info("Password reset requested for " + user + " but user has no local credential");
            throw new ExCannotResetPassword();
        }

        String token = Base62CodeGenerator.generate();
        _db.insertPasswordResetToken(user.id(), token);
        _passwordResetEmailer.sendPasswordResetEmail(user.id(), token);
        l.info("Password Reset Email sent to " + user.id());
    }

    public void resetPassword(String passwordResetToken, ByteString newCredentials)
            throws SQLException, ExNotFound, IOException, MessagingException
    {
        UserID userId = _db.resolvePasswordResetToken(passwordResetToken);
        User user = _factUser.create(userId);
        user.throwIfNotFound();
        _db.updateUserCredentials(user.id(), SPParam.getShaedSP(newCredentials.toByteArray()));
        _db.deletePasswordResetToken(passwordResetToken);
        l.info("Reset " + userId + "'s Password");
        _passwordResetEmailer.sendPasswordResetConfirmation(user.id());
    }

    // TODO (WW) move it to the User class
    public void changePassword(UserID userId, ByteString old_credentials,
            ByteString new_credentials)
            throws ExNotFound, IOException, SQLException, ExNoPerm
    {
        User user = _factUser.create(userId);
        user.throwIfNotFound();
        _db.checkAndUpdateUserCredentials(user.id(),
                SPParam.getShaedSP(old_credentials.toByteArray()),
                SPParam.getShaedSP(new_credentials.toByteArray()));
        l.info(userId + "'s Password was successfully changed");
    }

    // This function points to a lack of encapsulation elsewhere. I do not like it; I hate it.
    // We can't just ask "!isInternalUser" since that will never be true for PROD mode
    private boolean canResetPassword(User user)
    {
        return     !PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT
                || (Identity.AUTHENTICATOR == Authenticator.LOCAL_CREDENTIAL)
                || (!_userFilter.isInternalUser(user.id()));
    }
}

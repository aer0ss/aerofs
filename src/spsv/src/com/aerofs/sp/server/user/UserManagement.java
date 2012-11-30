package com.aerofs.sp.server.user;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

/**
 * TODO (WW) move all methods from this class to User or Organization classes
 */
public class UserManagement
{
    private final SPDatabase _db;
    private final InvitationEmailer.Factory _emailerFactory;
    private final PasswordResetEmailer _passwordResetEmailer;
    private final User.Factory _factUser;

    private static final Logger l = Util.l(UserManagement.class);

    public UserManagement(SPDatabase db, User.Factory factUser,
            InvitationEmailer.Factory emailerFactory, PasswordResetEmailer passwordResetEmailer)
    {
        _db = db;
        _factUser = factUser;
        _emailerFactory = emailerFactory;
        _passwordResetEmailer = passwordResetEmailer;
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
            throws ExAlreadyExist, ExNotFound, SQLException, IOException
    {
        assert inviteeId != null;

        // Check that the invitee doesn't exist already
        if (_factUser.create(inviteeId).exists()) throw new ExAlreadyExist("user already exists");

        final String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);

        _db.addTargetedSignupCode(code, inviter.id(), inviteeId, inviteeOrg.id());

        _db.addEmailSubscription(inviteeId, SubscriptionCategory.AEROFS_INVITATION_REMINDER);

        return _emailerFactory.createUserInvitation(inviter.id().toString(), inviteeId.toString(),
                inviter.getFullName()._first, folderName, note, code);
    }

    public void sendPasswordResetEmail(User user)
            throws SQLException, IOException, MessagingException
    {
        if (!user.exists()) {
            // If we don't have a user, just do nothing
            l.info("Password reset requested for " + user + " but user doesn't exist");
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
        User user = _factUser.create(userId);
        user.throwIfNotFound();
        _db.updateUserCredentials(user.id(), SPParam.getShaedSP(newCredentials.toByteArray()));
        _db.deletePasswordResetToken(passwordResetToken);
        l.info("Reset " + userId + "'s Password");
        _passwordResetEmailer.sendPasswordResetConfirmation(user.id());
    }

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
}

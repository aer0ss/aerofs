/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.lib.FullName;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.proto.Sp.SPServiceBlockingStub;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

/**
 * Performs the AeroFS client-side logic to Sign Up a user with SP.
 */
class SignupHelper
{
    private static final Logger l = Util.l(SignupHelper.class);
    private final SPServiceBlockingStub _sp;

    SignupHelper(SPServiceBlockingStub sp)
    {
        assert sp != null;
        _sp = sp;
    }

    /**
     * Sign-up the userId with SP, then sign-in for further action SP.
     */
    void signUp(UserID userId, byte[] scrypted, String signUpCode, FullName fullName)
            throws Exception
    {
        ByteString bscrypted = ByteString.copyFrom(scrypted);

        try {
            parseCodeAndSignUp(signUpCode, userId, bscrypted, fullName);
        } catch (ExAlreadyExist e) {
            // If we get an AlreadyExist, maybe it's because a previous sign-up succeeded
            // on the server but some error happened later on the client side.
            // So let's see if we can sign-in:
            try {
                _sp.signIn(userId.toString(), bscrypted);
            } catch (Exception e2) {
                // nope, sign-in didn't work either. The user definitely already exist with
                // a different password. Throw the original ExAlreadyExist.
                throw e;
            }
        }
    }

    private void parseCodeAndSignUp(String signUpCode, UserID userId, ByteString bscrypted,
            FullName fullName)
            throws Exception
    {
        CodeType type = InvitationCode.getType(signUpCode);
        switch (type) {
        case TARGETED_SIGNUP:
            _sp.signUpWithTargeted(signUpCode, bscrypted, fullName._first, fullName._last);
            break;
        default:
            if (signUpCode.isEmpty() && Cfg.staging()) {
                // special path for syncdet and other headless installs
                _sp.signUp(userId.toString(), bscrypted, fullName._first, fullName._last);
                break;
            } else {
                // Currently we don't allow signing-up without a sign-up code
                l.warn("Invalid signup code " + signUpCode + " of type " + type);
                throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
            }
        }
    }
}

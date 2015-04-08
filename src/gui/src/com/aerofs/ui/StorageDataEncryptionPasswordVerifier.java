/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui;

import com.aerofs.lib.Util;

import java.util.Arrays;

public class StorageDataEncryptionPasswordVerifier
{
    private static final int MIN_PASSWD_LENGTH = 6;

    public enum PasswordVerifierResult {
        TOO_SHORT("Password is too short"),
        INVALID_CHARACTERS ("Password contains invalid characters"),
        PASSWORDS_DO_NOT_MATCH ("Passwords do not match"),
        OK;

        private String _msg;

        PasswordVerifierResult() {
            this("");
        }

        PasswordVerifierResult(String msg)
        {
           _msg = msg;
        }

        public String getMsg()
        {
            return _msg;
        }
    }

    public PasswordVerifierResult verifyAndConfirmPasswords(char[] passwd, char[] passwd2)
    {
        PasswordVerifierResult result = verifyPassword(passwd);
        if (result == PasswordVerifierResult.OK) {
            result = confirmPasswords(passwd, passwd2);
        }
        return result;
    }

    public PasswordVerifierResult verifyPassword(char[] passwd)
    {
        if (passwd.length < MIN_PASSWD_LENGTH) return PasswordVerifierResult.TOO_SHORT;
        if (!isValidPassword(passwd)) return PasswordVerifierResult.INVALID_CHARACTERS;
        return PasswordVerifierResult.OK;
    }

    public PasswordVerifierResult confirmPasswords(char[] passwd, char[] passwd2)
    {
        if (!Arrays.equals(passwd, passwd2)) {
            return PasswordVerifierResult.PASSWORDS_DO_NOT_MATCH;
        } else {
            return PasswordVerifierResult.OK;
        }
    }

    private boolean isValidPassword(char[] passwd)
    {
        for (int i = 0; i < passwd.length; i++) {
            if (!Util.isASCII(passwd[i])) return false;
        }

        return true;
    }
}

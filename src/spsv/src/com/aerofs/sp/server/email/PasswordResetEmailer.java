/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import javax.mail.internet.MimeMessage;

import static com.aerofs.sp.server.SPSVParam.SP_EMAIL_ADDRESS;


public class PasswordResetEmailer
{
    public void sendPasswordResetEmail(String to, String reset_token)
            throws Exception
    {
        String body = "Somebody requested that your password be reset.  If it wasn't you, " +
                "ignore this message.  To reset your password, use the following token: " +
                reset_token;
        MimeMessage msg = EmailUtil.composeEmail(SP_EMAIL_ADDRESS,null,to,null,
                "Reset your password",body);
        EmailUtil.sendEmail(msg,true);
    }
}

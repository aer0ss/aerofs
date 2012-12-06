/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.lib.Param.SV;

import javax.annotation.Nullable;
import java.io.IOException;

public class Email implements IEmail
{
    static final String DEFAULT_PS = "Have questions or comments? Email us at " +
            SV.SUPPORT_EMAIL_ADDRESS;

    private final HTMLEmail _htmlEmail;
    private final TextEmail _textEmail;

    public Email(String subject)
    {
        this(subject, false, null);
    }

    public Email(final String subject, boolean unsubscribe, @Nullable String blobId)
    {
        _htmlEmail = new HTMLEmail(subject, unsubscribe, blobId);
        _textEmail = new TextEmail(unsubscribe, blobId);
    }

    @Override
    public void addSection(String header, HEADER_SIZE size, String body) throws IOException
    {
        _htmlEmail.addSection(header, size, body);
        _textEmail.addSection(header, size, body);
    }

    @Override
    public void addSignature(String valediction, String name, String ps) throws IOException
    {
        _htmlEmail.addSignature(valediction, name, ps);
        _textEmail.addSignature(valediction, name, ps);
    }

    public String getTextEmail() {
        return _textEmail.getEmail();
    }

    public String getHTMLEmail()
    {
        return _htmlEmail.getEmail();
    }
}
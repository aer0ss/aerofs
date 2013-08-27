/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.labeling.L;
import org.apache.commons.lang.StringEscapeUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Email implements IEmail
{
    static final String DEFAULT_PS = "Have questions or comments? Email us at " +
            WWW.SUPPORT_EMAIL_ADDRESS;

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
        _htmlEmail.addSection(header, size, htmlified(body));
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

    public void addDefaultSignature() throws IOException
    {
        addSignature("Happy Syncing,", "The " + L.brand() + " Team", Email.DEFAULT_PS);
    }

    // A crappy linkifier
    // Don't pass already-marked-up text to this function, it's dumb and will double-link the text
    // TODO (DF): replace this whole module with a real template engine
    private String crappyLinkified(String plain)
    {
        Pattern pattern = Pattern.compile(
                // A link is a known schema + host
                "http(s)?://([\\w+?\\.\\w+])+" +
                // plus an optional port and path
                "([a-zA-Z0-9~!@#\\$%\\^&\\*\\(\\)_\\-=\\+\\\\/\\?\\.:;',]*)?",
            Pattern.DOTALL | Pattern.UNIX_LINES | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(plain);
        return matcher.replaceAll("<a href=\"$0\">$0</a>");
    }
    private String htmlified(String plain)
    {
        String escaped = StringEscapeUtils.escapeHtml(plain);
        String linkified = crappyLinkified(escaped);
        return linkified;
    }
}

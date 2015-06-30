/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.email;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.sp.server.lib.SPParam;
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

    public Email()
    {
        this(false, null);
    }

    public Email(boolean unsubscribe, @Nullable String blobId)
    {
        _htmlEmail = new HTMLEmail(unsubscribe, blobId);
        _textEmail = new TextEmail(unsubscribe, blobId);
    }

    @Override
    public void addSection(String header, String body) throws IOException
    {
        _htmlEmail.addSection(header, htmlified(body));
        _textEmail.addSection(header, body);
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
        addSignature("Happy Syncing,", "The " + SPParam.BRAND + " Team", Email.DEFAULT_PS);
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
        return crappyLinkified(escaped);
    }
}

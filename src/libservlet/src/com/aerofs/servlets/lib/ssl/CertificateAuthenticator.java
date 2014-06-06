/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.ssl;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.server.IRequestProvider;

import java.io.StringReader;
import java.util.Properties;
import java.io.IOException;

public class CertificateAuthenticator
{
    private final IRequestProvider _request;

    public CertificateAuthenticator(IRequestProvider request)
    {
        _request = request;
    }

    /**
     * Return true when the nginx mutal authentication using certificates is successful. When the
     * object has not yet been set, return false.
     */
    public boolean isAuthenticated()
    {
        return _request.get().getHeader("Verify").equalsIgnoreCase("SUCCESS");
    }

    /**
     * Returns the serial number of the authenticated certificate.
     * @throws com.aerofs.base.ex.ExBadCredential if the session has not been authenticated.
     */
    public long getSerial()
            throws ExBadCredential
    {
        if (!isAuthenticated()) {
            throw new ExBadCredential();
        }
        return Long.parseLong(_request.get().getHeader("Serial"), 16);
    }

    /**
     * Return the cname of the client certificate.
     * @throws com.aerofs.base.ex.ExBadCredential if the session has not been authenticated.
     */
    public String getCName()
            throws ExBadCredential
    {
        if (!isAuthenticated()) {
            throw new ExBadCredential();
        }
        Properties props = new Properties();
        String dname = _request.get().getHeader("DName");
        try {
            props.load(new StringReader(dname.replaceAll("/", "\n")));
        } catch (IOException e) {
            // squash.  nginx should never give us an invalidly-formatted DName.
        }
        return (String) props.get("CN");
    }
}

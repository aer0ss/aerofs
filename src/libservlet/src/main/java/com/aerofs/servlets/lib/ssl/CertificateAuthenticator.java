/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.ssl;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.CertAuthExtractor;
import com.aerofs.sp.server.IRequestProvider;

public class CertificateAuthenticator
{
    private final IRequestProvider _request;

    public CertificateAuthenticator(IRequestProvider request)
    {
        _request = request;
    }

    /**
     * Return true when the nginx mutual authentication using certificates is successful. When the
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
    public String getCName() throws ExBadCredential
    {
        if (!isAuthenticated()) {
            throw new ExBadCredential();
        }
        return CertAuthExtractor.CNFromDName(_request.get().getHeader("DName"));
    }
}

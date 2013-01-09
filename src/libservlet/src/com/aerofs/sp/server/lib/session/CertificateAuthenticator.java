/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.base.ex.ExBadCredential;

public class CertificateAuthenticator
        extends AbstractHttpSession
{
    private static final String SESS_ATTR_CERTAUTH_SERIAL  = "certauth_serial";

    public CertificateAuthenticator(IHttpSessionProvider sessionProvider)
    {
        super(sessionProvider);
    }

    /**
     * Return true when the nginx mutal authentication using certificates is successful. When the
     * object has not yet been set, return false.
     */
    public boolean isAuthenticated()
    {
        return getSession().getAttribute(SESS_ATTR_CERTAUTH_SERIAL) != null;
    }

    /**
     * Returns the serial number of the authenticated certificiate.
     * @throws com.aerofs.base.ex.ExBadCredential if the session has not been authenticated.
     */
    public long getSerial() throws ExBadCredential
    {
        if (!isAuthenticated()) {
            throw new ExBadCredential();
        }

        String serial = (String) getSession().getAttribute(SESS_ATTR_CERTAUTH_SERIAL);
        assert serial != null;

        // The initial conversion from long to string is done by nginx.
        return Long.parseLong(serial, 16);
    }

    public void set(boolean authenticated, String serial)
    {
        if (authenticated) {
            getSession().setAttribute(SESS_ATTR_CERTAUTH_SERIAL, serial);
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.session;

import java.security.cert.X509Certificate;
import com.aerofs.base.ex.ExBadCredential;

public class CertificateAuthenticator
        extends AbstractHttpSession
{
    private static final String SESS_ATTR_CERTAUTH_SERIAL  = "certauth_serial";
    private static final String SESS_ATTR_CERTAUTH_CNAME  = "certauth_cname";

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
        // Either attribute will work.
        return getSession().getAttribute(SESS_ATTR_CERTAUTH_SERIAL) != null;
    }

    /**
     * Returns the serial number of the authenticated certificiate.
     * @throws com.aerofs.base.ex.ExBadCredential if the session has not been authenticated.
     */
    public long getSerial()
            throws ExBadCredential
    {
        if (!isAuthenticated()) {
            throw new ExBadCredential();
        }

        return (Long) getSession().getAttribute(SESS_ATTR_CERTAUTH_SERIAL);
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
        return (String) getSession().getAttribute(SESS_ATTR_CERTAUTH_CNAME);
    }

    public void set(boolean authenticated, Long serial, String cname)
    {
        if (authenticated) {
            getSession().setAttribute(SESS_ATTR_CERTAUTH_SERIAL, serial);
            getSession().setAttribute(SESS_ATTR_CERTAUTH_CNAME, cname);
        }
    }
}

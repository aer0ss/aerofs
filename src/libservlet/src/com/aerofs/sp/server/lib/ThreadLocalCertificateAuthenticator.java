/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.base.ex.ExBadCredential;

public class ThreadLocalCertificateAuthenticator
        extends AbstractThreadLocalHttpSession
{
    private static final String SESS_ATTR_CERTAUTH_SERIAL  = "certauth_serial";

    /**
     * Return true when the nginx mutal authentication using certificates is successful. When the
     * object has not yet been set, return false.
     */
    public boolean isAuthenticated()
    {
        return _session.get().getAttribute(SESS_ATTR_CERTAUTH_SERIAL) != null;
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

        String serial = (String) _session.get().getAttribute(SESS_ATTR_CERTAUTH_SERIAL);
        assert serial != null;

        // The initial conversion from long to string is done by nginx.
        return Long.parseLong(serial, 16);
    }

    public void set(boolean authenticated, String serial)
    {
        if (authenticated) {
            _session.get().setAttribute(SESS_ATTR_CERTAUTH_SERIAL, serial);
        }
    }
}
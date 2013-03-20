/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.ssl;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.server.lib.session.AbstractHttpSession;
import com.aerofs.sp.server.lib.session.IHttpSessionProvider;

import javax.servlet.http.HttpServletRequest;
import java.io.StringReader;
import java.util.Properties;
import java.io.IOException;

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
     * Initialize the certificate authenticator using a given http servlet request. Pulls request
     * headers from the request and sets session related information.
     */
    public void init(HttpServletRequest req)
            throws IOException
    {
        String verify = req.getHeader("Verify");
        String serial = req.getHeader("Serial");
        String dname = req.getHeader("DName");

        if (verify != null && serial != null && dname != null) {
            Properties prop = new Properties();
            prop.load(new StringReader(dname.replaceAll("/", "\n")));
            String cname = (String) prop.get("CN");

            // The "Verify" header corresponds to the nginx variable $ssl_client_verify which
            // is set to "SUCCESS" when nginx mutual authentication is successful.
            set(verify.equalsIgnoreCase("SUCCESS"), Long.parseLong(serial, 16), cname);
        }
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

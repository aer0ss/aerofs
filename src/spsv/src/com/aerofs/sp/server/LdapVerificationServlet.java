/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.sp.authentication.ExLdapConfigurationError;
import com.aerofs.sp.authentication.LdapAuthority;
import com.aerofs.sp.authentication.LdapConfiguration;
import com.unboundid.ldap.sdk.LDAPException;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * This class is used by the site configuration UI to verify LDAP settings during setup.
 */
public class LdapVerificationServlet extends HttpServlet
{
    private static final Logger l = Loggers.getLogger(LdapVerificationServlet.class);
    private static final long serialVersionUID = 1L;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
    }

    private @Nonnull String getParameter(HttpServletRequest req, String parameterName)
            throws IOException
    {
        String parameter = req.getParameter(parameterName);

        if (parameter == null) {
            throw new IOException("Missing required parameter " + parameterName);
        }

        return parameter;
    }

    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
            throws IOException
    {
        try {
            LdapConfiguration lcfg = new LdapConfiguration();

            // These parameter names must match the external property key names (see the config
            // package).
            lcfg.SERVER_HOST       = getParameter(req, "ldap_server_host");
            lcfg.SERVER_PORT       = Integer.parseInt(getParameter(req, "ldap_server_port"));
            lcfg.USER_BASE         = getParameter(req, "ldap_server_schema_user_base");
            lcfg.SERVER_PRINCIPAL  = getParameter(req, "ldap_server_principal");
            lcfg.SERVER_CREDENTIAL = getParameter(req, "ldap_server_credential");
            lcfg.SERVER_SECURITY   = LdapConfiguration.convertStringToSecurityType(
                    getParameter(req, "ldap_server_security"));
            lcfg.USER_SCOPE        = getParameter(req, "ldap_server_schema_user_scope");
            lcfg.USER_FIRSTNAME    = getParameter(req, "ldap_server_schema_user_field_firstname");
            lcfg.USER_LASTNAME     = getParameter(req, "ldap_server_schema_user_field_lastname");
            lcfg.USER_EMAIL        = getParameter(req, "ldap_server_schema_user_field_email");
            lcfg.USER_RDN          = getParameter(req, "ldap_server_schema_user_field_rdn");
            lcfg.USER_OBJECTCLASS  = getParameter(req, "ldap_server_schema_user_class");
            lcfg.SERVER_CA_CERT    = getParameter(req, "ldap_server_ca_certificate");
            lcfg.USER_ADDITIONALFILTER = getParameter(req, "ldap_server_schema_user_filter");

            LdapAuthority lauth = new LdapAuthority(lcfg);

            try {
                lauth.testConnection();
            } catch (ExExternalServiceUnavailable | LDAPException e) {
                l.error("Error connecting to LDAP server: " + Exceptions.getStackTraceAsString(e));
                resp.setStatus(400);
                resp.getWriter().print(e.getMessage());
            }
        } catch (ExLdapConfigurationError e) {
            l.error("Configuration error: " + Exceptions.getStackTraceAsString(e));
            throw new IOException(e);
        }
    }

    /**
     * This servlet does not support GET.
     *
     * Return 200 anyway for the purposes of sanity checking.
     * */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        resp.setStatus(200);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * LDAP server and schema configuration.
 */
public class LdapConfiguration
{
    /** True if the server is configured to use LDAP to authenticate endusers. */
    public static boolean enabled()
    {
        return Identity.AUTHENTICATOR == Authenticator.EXTERNAL_CREDENTIAL;
    }

    public enum SecurityType
    {
        /** The server does not support any socket-level security. Strongly not recommended. */
        NONE,
        /** The server supports LDAP over SSL (the "ldaps" protocol). */
        SSL,
        /** The server supports the StartTLS extension. */
        STARTTLS
    }

    /**
     * If the LDAP server does not have a publicly-signed certificate, the cert can
     * be supplied here. It will be added to the trust store only for LDAP server connections.
     *
     * No default.
     */
    public String                                   SERVER_CA_CERT =
            getStringProperty(                      "ldap.server.ca_certificate", "");

    /**
     * Host name of the LDAP server.
     */
    public String                                   SERVER_HOST =
            getStringProperty(                      "ldap.server.host", "");

    /**
     * Port on which to connect to the LDAP server. Default is 389 for ldap protocol,
     * 636 for ldaps.
     */
    public Integer                                  SERVER_PORT =
            getIntegerProperty(                     "ldap.server.port", 389);

    /**
     * Configure the socket-level security type used by the LDAP server. The options are:
     *
     *  None : use unencrypted socket (Not recommended, as this could expose user credentials
     *  to network snoopers)
     *
     *  SSL : use LDAP over SSL (the ldaps protocol).
     *
     *  TLS : use the LDAP StartTLS extension.
     *
     */
    public SecurityType                             SERVER_SECURITY =
            convertProperty(                        "ldap.server.security", "TLS");

    /**
     * Maximum number of LDAP connection instances to keep in the pool.
     */
    public Integer                                  SERVER_MAXCONN =
            getIntegerProperty(                     "ldap.server.maxconn", 10);

    /**
     * If true, a user with no record in AeroFS will be created the first time they
     * successfully authenticate with the configured LDAP server.
     * If false, the user will be denied login until they are explicitly provisioned.
     */
    public Boolean                                  SERVER_AUTOPROVISION =
            getBooleanProperty(                     "ldap.server.autoprovision", true);

    /**
     * Timeout, in seconds, after which a server read operation will be cancelled.
     */
    public Integer                                  SERVER_TIMEOUT_READ =
            getIntegerProperty(                     "ldap.server.timeout.read", 180);

    /**
     * Timeout, in seconds, after which a server connect attempt will be abandoned.
     */
    public Integer                                  SERVER_TIMEOUT_CONNECT =
            getIntegerProperty(                     "ldap.server.timeout.connect", 60);

    /**
     * Principal on the LDAP server to use for the initial user search.
     */
    public String                                   SERVER_PRINCIPAL =
            getStringProperty(                      "ldap.server.principal", "");

    /**
     * Credential on the LDAP server for the search principal.
     */
    public String                                   SERVER_CREDENTIAL =
            getStringProperty(                      "ldap.server.credential", "");

    /**
     * Distinguished Name (dn) of the root of the tree within the LDAP server in which
     * user accounts are found. More specific DNs are preferred.
     */
    public String                                   USER_BASE =
            getStringProperty(                      "ldap.server.schema.user.base", "");

    /**
     * The scope to search for user records. Valid values are "base", "one", or "subtree".
     * The default is "subtree".
     */
    public String                                   USER_SCOPE =
            getStringProperty(                      "ldap.server.schema.user.scope", "subtree");

    /**
     * The field name in the LDAP record of the user's first name.
     */
    public String                                   USER_FIRSTNAME =
            getStringProperty(                      "ldap.server.schema.user.field.firstname", "");

    /**
     * The name of the field in the LDAP record of the user's surname (last name).
     */
    public String                                   USER_LASTNAME =
            getStringProperty(                      "ldap.server.schema.user.field.lastname", "");

    /**
     * The name of the field in the LDAP record of the user's email address. This will
     * used in the user search.
     */
    public String                                   USER_EMAIL =
            getStringProperty(                      "ldap.server.schema.user.field.email", "");

    /**
     * The name of the field that contains the user's relative distinguished name - that is,
     * the field that will be used in the bind attempt.
     */
    public String                                   USER_RDN =
            getStringProperty(                      "ldap.server.schema.user.field.rdn", "");

    /**
     * The required object class of the user record. This will be used in the user search.
     */
    public String                                   USER_OBJECTCLASS =
            getStringProperty(                      "ldap.server.schema.user.class", "");

    // A quick converter to an enum that falls back to a default rather than throw IllegalArg
    static SecurityType convertProperty(String paramName, String paramDefault)
    {
        // Maintain this code carefully! The valid configuration names are maintained separately
        // from the actual enum - one is public-visible and the other is developers only.
        String value = getStringProperty(paramName, paramDefault).toUpperCase();

        if (value.equals("TLS")) {
            return SecurityType.STARTTLS;
        } else if (value.equals("NONE")) {
            return SecurityType.NONE;
        } else if (value.equals("SSL")) {
            return SecurityType.SSL;
        }
        // Uhh, so...who will catch this?
        throw new ExLdapConfigurationError("Unknown security type " + value);
    }
}

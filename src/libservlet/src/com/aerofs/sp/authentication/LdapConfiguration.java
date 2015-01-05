/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.google.common.base.Splitter;
import com.unboundid.ldap.sdk.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * LDAP server and schema configuration.
 *
 * TODO (MP) refactor this class.
 * We should really only try to pull these values out of configuration if we're in LDAP mode. Trying
 * to parse them every time is just annoying.
 */
public class LdapConfiguration
{
    // spaces are not permitted in LDAP objectClass names nor attribute names, so we can use them
    // to delimit the separate items in a serialized list
    // make sure this value is consistent with the value in identity_view.py
    public final static String LDAP_SEPARATOR = " ";

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
            getStringProperty("ldap.server.host", "");

    /**
     * Port on which to connect to the LDAP server. Default is 389 for ldap protocol,
     * 636 for ldaps.
     */
    public Integer                                  SERVER_PORT =
            getIntegerProperty("ldap.server.port", 389);

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
            convertPropertyNameToSecurityType("ldap.server.security", "TLS");

    /**
     * Maximum number of LDAP connection instances to keep in the pool.
     */
    public Integer                                  SERVER_MAXCONN =
            getIntegerProperty(                     "ldap.server.maxconn", 10);

    /**
     * Timeout, in seconds, after which a server read operation will be cancelled.
     */
    public Integer                                  SERVER_TIMEOUT_READ =
            getIntegerProperty(                     "ldap.server.timeout.read", 5);

    /**
     * Timeout, in seconds, after which a server connect attempt will be abandoned.
     */
    public Integer                                  SERVER_TIMEOUT_CONNECT =
            getIntegerProperty("ldap.server.timeout.connect", 5);

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
    public SearchScope                              USER_SCOPE =
            convertStringToSearchScope(getStringProperty(
                                                    "ldap.server.schema.user.scope", "subtree"));

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

    /**
     * An extra LDAP filter used only when authenticating users, any user who passes all the other
     * requirements but fails this one will be unable to sign up for or sign into AeroFS
     */
    public String                                   USER_ADDITIONALFILTER =
            getStringProperty(                      "ldap.server.schema.user.filter", "");

    /**
     * A space-separated list of object classes which correspond to groups in the LDAP tree
     */
    public List<String>                             GROUP_OBJECTCLASSES =
            splitIntoList(getStringProperty(        "ldap.server.schema.group.class", ""));

    /**
     * The name of the field that will contain the group's name as displayed by AeroFS
     */
    public String                                   GROUP_NAME =
            getStringProperty(                      "ldap.server.schema.group.name", "");

    /**
     * The Distinguished Name of the base of the subtree in LDAP which AeroFS will sync groups from
     */
    public String                                   GROUP_BASE =
            getStringProperty(                      "ldap.server.schema.group.base", "");

    /**
     * The scope from the GROUP_BASE within which we sync groups
     */
    public SearchScope                              GROUP_SCOPE =
            convertStringToSearchScope(getStringProperty(
                                                    "ldap.server.schema.group.scope", "subtree"));

    /**
     * A space-separated list of group member attribute names whose value is the DN of that member
     */
    public List<String>                             GROUP_STATIC_MEMBERS =
            splitIntoList(getStringProperty(        "ldap.server.schema.group.member.static", ""));

    /**
     * A space-separated list of group member attribute names whose value is a LDAP search which
     * defines members of the group
     */
    public List<String>                             GROUP_DYNAMIC_MEMBERS =
            splitIntoList(getStringProperty(        "ldap.server.schema.group.member.dynamic", ""));

    /**
     * The name of the member attribute which has as its value a unique value found on the member
     */
    public String                                   GROUP_UID_MEMBER =
            getStringProperty(                      "ldap.server.schema.group.member.unique", "");

    /**
     * The name of the attribute that uniquely specifies group members, and whose value is specified
     * by the GROUP_UID_MEMBER attribute on the LDAP group entry
     */
    public String                                   COMMON_UID_ATTRIBUTE =
            getStringProperty(                      "ldap.server.schema.user.uid", "");

    private List<String> splitIntoList(String serialized)
    {
        return Splitter.on(LDAP_SEPARATOR)
                .trimResults()
                .omitEmptyStrings()
                .splitToList(serialized);
    }

    /**
     * A quick converter from a configuration property name to an enum that falls back to a default
     * rather than throw IllegalArg.
     */
    public static SecurityType convertPropertyNameToSecurityType(String propertyName,
            String defaultValue)
    {
        // Maintain this code carefully! The valid configuration names are maintained separately
        // from the actual enum - one is public-visible and the other is developers only.
        String value = getStringProperty(propertyName, defaultValue).toUpperCase();
        return convertStringToSecurityType(value);
    }

    public static SearchScope convertStringToSearchScope(String propertyValue)
    {
        switch (propertyValue) {
        case "base":
            return SearchScope.BASE;
        case "one":
            return SearchScope.ONE;
        case "subtree":
            return SearchScope.SUB;
        default:
            _l.error("unrecognized scope {}, defaulting to subtree", propertyValue);
            return SearchScope.SUB;
        }
    }

    /**
     * Convert a string to a security type.
     * TODO (MP) this is used by the LdapVerificationServlet. Might want to refactor to be more user friendly.
     */
    public static SecurityType convertStringToSecurityType(String inputString)
    {
        inputString = inputString.toUpperCase();

        if (inputString.equals("TLS")) {
            return SecurityType.STARTTLS;
        } else if (inputString.equals("NONE")) {
            return SecurityType.NONE;
        } else if (inputString.equals("SSL")) {
            return SecurityType.SSL;
        }

        // Uhh, so...who will catch this?
        throw new ExLdapConfigurationError("Unknown security type " + inputString);
    }

    private static Logger _l = LoggerFactory.getLogger(LdapConfiguration.class);
}

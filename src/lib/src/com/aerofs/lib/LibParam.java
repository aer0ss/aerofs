/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;

import java.net.InetSocketAddress;
import java.net.URL;

import static com.aerofs.base.config.ConfigurationProperties.*;

/**
 * Note that Main has dependencies on this class before the configuration is initialized. Hence
 *   this class must not have static fields that are dependent on the configuration subsystem.
 * The subclasses, on the other hand, can have static fields that are dependent on the configuration
 *   subsystem.
 *
 * For more information, Google how Java class loader works regarding static initializers.
 */
public class LibParam extends BaseParam
{
    public static final long EXP_RETRY_MIN_DEFAULT           = 2 * C.SEC;
    public static final long EXP_RETRY_MAX_DEFAULT           = 60 * C.SEC;


    // Epochs initial values
    public static final int INITIAL_ACL_EPOCH = 0;

    public static class CA
    {
        public static final URL URL = getURL("http://ca.service:9002/prod");

        private static URL getURL(String url) {
            try {
                return new URL(url);
            } catch (java.net.MalformedURLException e) {
                throw com.google.common.base.Throwables.propagate(e);
            }
        }
    }

    public static class REDIS
    {
        public static final InetSocketAddress AOF_ADDRESS =
                InetSocketAddress.createUnresolved(
                        getNonEmptyStringProperty("redis.host", "localhost"),
                        getIntegerProperty("redis.port", 6379));

        // jedisConnectionPool doesn't treat an empty string the same as having no password,
        // so we convert an empty property to null
        public static final String PASSWORD =
                Util.returnNullIfEmpty(getStringProperty("redis.password"));
    }

    public static class MYSQL
    {
        public static final String MYSQL_ADDRESS =
                getNonEmptyStringProperty("mysql.url", "localhost");

        public static final String MYSQL_DRIVER =
                "com.mysql.jdbc.Driver";

        public static final String MYSQL_USER =
                getNonEmptyStringProperty("mysql.user", "aerofsdb");

        public static final String MYSQL_PASS =
                getStringProperty("mysql.password");
    }

    public static class LicenseProperties
    {
        public static final String VALID_UNTIL = "license_valid_until";
        public static final String LICENSE_SEATS = "license_seats";
        public static final String CUSTOMER_ID = "customer_id";
        public static final String CUSTOMER_NAME = "license_company";
    }

    /**
     * Parameters for identity management - signin and authentication.
     * TODO: convert members to non-static
     */
    public static class Identity
    {
        /**
         * The user-authentication type allowed by the server. Default is LOCAL_CREDENTIAL.
         */
        public enum Authenticator
        {
            /**
             * The user will provide a username and credential that will be verified
             * locally (on the signin server). This implies the credential will be scrypt'ed.
             */
            LOCAL_CREDENTIAL,
            /**
             * The user will prove their identity using a username and credential that will be
             * passed through to an identity authority (LDAP). This implies the credential should not be
             * hashed on the client side.
             */
            EXTERNAL_CREDENTIAL,
            /**
             * The user will prove their identity out-of-band with a URI-based signin mechanism.
             *
             * This means the client will use the SessionNonce/DelegateNonce mechanism and
             * poll for the asynchronous authentication completion.
             *
             * We don't care about what web method is used, but the client can expect some
             * user-agent redirect to the IdentityServlet.
             *
             * Client should poll on the session nonce for the out-of-band authentication
             * to complete.
             */
            OPENID
        }

        /**
         * Choose a user authenticator style - this will determine the sign-in options
         * we show to the end-user.
         *
         * Valid values are:
         * <ul>
         *  <li>local_credential (check the supplied credential against the SP database)</li>
         *  <li>external_credential (check the supplied credential against an external
         *  user service, e.g. LDAP. This does not currently fall back to local_credential.)</li>
         *  <li>openid (IdentityServlet will support a signin request with web authentication)</li>
         *  </ul>
         */
        public static Authenticator                     AUTHENTICATOR =
                convertProperty(                        "lib.authenticator", "local_credential");

        // A quick converter to an enum that falls back to a default rather than throw IllegalArg
        static public Authenticator convertProperty(String paramName, String paramDefault)
        {
            // Maintain this code carefully! The valid configuration names are maintained separately
            // from the actual enum - one is public-visible and the other is developers only.
            String value = getStringProperty(paramName, paramDefault).toUpperCase();
            if (value.equals("OPENID")) {
                return Authenticator.OPENID;
            } else if (value.equals("EXTERNAL_CREDENTIAL")) {
                return Authenticator.EXTERNAL_CREDENTIAL;
            } else {
                return Authenticator.LOCAL_CREDENTIAL;
            }
        }

        /**
         * A short, user-visible name for the OpenID service. This will be displayed
         * to end-users in the context of "Sign in with {}", "A user without {} accounts", etc.
         */
        public static final String                      SERVICE_IDENTIFIER =
                getStringProperty("identity_service_identifier",
                        // The default value
                        OpenId.enabled() ? "OpenID" : "LDAP");
    }

    /**
     * OpenID and Identity-related configuration that are used by client and server.
     *
     * openid.service : configuration for the IdentityServlet (our intermediary)
     *
     * openid.idp : configuration for an OpenID provider.
     */
    public static class OpenId
    {
        public static boolean enabled()
        {
            return Identity.AUTHENTICATOR == Identity.Authenticator.OPENID;
        }

        /** Timeout for the entire OpenID flow, in seconds. */
        public static final Integer                     DELEGATE_TIMEOUT =
                getIntegerProperty(                     "openid.service.timeout", 300);

        /**
         * Timeout for the session nonce, in seconds. This is the timeout
         * only after the delegate nonce is authorized but before the session nonce
         * gets used. This only needs to be as long as the retry interval in the session
         * client, plus the max latency of the session query.
         */
        public static final Integer                     SESSION_TIMEOUT =
                getIntegerProperty(                     "openid.service.session.timeout", 10);

        /**
         * Polling frequency of the client waiting for OpenID authorization to complete, in seconds.
         * TODO: sub-second resolution?
         */
        public static final Integer                     SESSION_INTERVAL =
                getIntegerProperty(                     "openid.service.session.interval", 1);

        /** URL of the Identity service */
        public static final String                      IDENTITY_URL =
                getStringProperty(                      "openid.service.url");

        /** The security realm for which we are requesting authorization */
        public static final String                      IDENTITY_REALM =
                getStringProperty(                      "openid.service.realm");

        /** The auth request path to append to the identity server URL. */
        public static final String                      IDENTITY_REQ_PATH = "/oa";

        /** The auth response path to append to the identity server URL. */
        public static final String                      IDENTITY_RESP_PATH = "/os";

        /** The delegate nonce parameter to pass to the auth request URL. */
        public static final String                      IDENTITY_REQ_PARAM = "token";

        // -- Attributes used only by server code:

        /**
         * The name of the delegate nonce to pass to the OpenID provider; used to
         * correlate the auth request and auth response.
         */
        public static final String                      OPENID_DELEGATE_NONCE = "sp.nonce";

        /**
         * The URL to redirect the completed transaction to. Optional; if not set, we will
         * try to close the browser (and suggest the user do so).
         */
        public static final String                      OPENID_ONCOMPLETE_URL = "sp.oncomplete";

        /** Endpoint URL used if discovery is not enabled for this OpenID Provider */
        public static final String                      ENDPOINT_URL =
                getStringProperty(                      "openid.idp.endpoint.url");

        /** If enabled, use Diffie-Helman association and a MAC to verify the auth result */
        public static final Boolean                     ENDPOINT_STATEFUL =
                getBooleanProperty(                     "openid.idp.endpoint.stateful", true);

        /** Name of the HTTP parameter we should use as the user identifier in an auth response. */
        public static final String                      IDP_USER_ATTR =
                getStringProperty(                      "openid.idp.user.uid.attribute",
                                                        "openid.identity");

        /**
         * An optional regex pattern for parsing the user identifier into capture groups. If this
         * is set, the capture groups will be available for use in the email/firstname/lastname
         * fields using the syntax uid[1], uid[2], uid[3], etc.
         *
         * NOTE: If this is not set, we don't do any pattern-matching (do less is cheaper)
         *
         * NOTE: capture groups are numbered starting at _1_.
         */
        public static final String                      IDP_USER_PATTERN =
                getStringProperty(                      "openid.idp.user.uid.pattern");


        /**
         * Name of the openid extension set to request, or can be empty. Supported extensions are:
         *
         * "ax" for attribute exchange
         *
         * "sreg" for simple registration (an OpenID 1.0 extension)
         */
        public static final String                      IDP_USER_EXTENSION =
                getStringProperty(                      "openid.idp.user.extension");

        /**
         * Name of an openid parameter that contains the user's email address; or a pattern that
         * uses the uid[n] syntax. It is an error to request a uid capture group if
         * openid.idp.user.uid.pattern is not set.
         *
         * Examples:
         *
         * openid.ext1.value.email
         *
         * uid[1]@syncfs.com
         */
        public static final String                      IDP_USER_EMAIL =
                getStringProperty(                      "openid.idp.user.email",
                                                        "openid.ext1.value.email");

        // TODO: support fullname for "sreg" providers and split by whitespace
        /**
         * Name of an openid parameter that contains the user's first name; or a pattern that
         * uses the uid[n] syntax. It is an error to request a uid capture group if
         * openid.idp.user.uid.pattern is not set.
         *
         * Example: openid.ext1.value.firstname (for ax)
         *
         * openid.sreg.fullname (for sreg; fullname only)
         */
        public static final String                      IDP_USER_FIRSTNAME =
                getStringProperty(                      "openid.idp.user.name.first",
                                                        "openid.ext1.value.firstname");

        /**
         * Name of an openid parameter that contains the user's last name; or a pattern that
         * uses the uid[n] syntax. It is an error to request a uid capture group if
         * openid.idp.user.uid.pattern is not set.
         *
         * Example: openid.ext1.value.lastname (for ax)
         *
         * openid.sreg.fullname (for sreg; fullname only)
         */
        public static final String                      IDP_USER_LASTNAME =
                getStringProperty(                      "openid.idp.user.name.last",
                                                        "openid.ext1.value.lastname");
    }

    public static class MobileDeviceManagement {
        public static Boolean                           IS_ENABLED =
                getBooleanProperty(                     "mobile.device.management.enabled",
                                                        false);

        public static String                            MDM_PROXIES =
                getStringProperty(                      "mobile.device.management.proxies");
    }
}

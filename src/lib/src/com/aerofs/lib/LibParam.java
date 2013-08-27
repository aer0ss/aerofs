/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;
import com.aerofs.base.id.SID;
import com.google.common.base.Optional;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;

import static com.aerofs.base.config.ConfigurationProperties.getAddressProperty;
import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getOptionalStringProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.base.config.ConfigurationProperties.getUrlProperty;
import static com.aerofs.lib.configuration.ClientConfigurationLoader.PROPERTY_BASE_CA_CERT;
import static com.aerofs.lib.configuration.ClientConfigurationLoader.PROPERTY_CONFIG_SERVICE_URL;
import static com.aerofs.lib.configuration.ClientConfigurationLoader.PROPERTY_IS_ENTERPRISE_DEPLOYMENT;

public class LibParam extends BaseParam
{
    // the block size used for content hashing and block storage (see BlockStorage)
    public static final int FILE_BLOCK_SIZE                  = 4 * C.MB;
    public static final long FREQUENT_DEFECT_SENDER_INTERVAL = 30 * C.MIN;
    public static final long EXP_RETRY_MIN_DEFAULT           = 2 * C.SEC;
    public static final long EXP_RETRY_MAX_DEFAULT           = 60 * C.SEC;

    ////////
    // file and folder names

    public static final String TRASH                   = ".trash$$$";
    public static final String AEROFS_JAR              = "aerofs.jar";
    public static final String PORTBASE                = "pb";
    public static final String NODM                    = "nodm";
    public static final String NOSTUN                  = "nostun";
    public static final String NOTCP                   = "notcp";
    public static final String NOZEPHYR                = "nozephyr";
    public static final String NOAUTOUPDATE            = "noautoupdate";
    public static final String NOXFF                   = "noxff";
    public static final String LOL                     = "lol";
    public static final String LOLOL                   = "lolol";
    public static final String AGGRESSIVE_CHECKS       = "ac";
    public static final String VERSION                 = "version";
    public static final String DEFAULT_RTROOT          = "DEFAULT";
    public static final String DEVICE_CERT             = "cert";
    public static final String DEVICE_KEY              = "key";
    public static final String CA_CERT                 = "cacert.pem";
    public static final String CORE_DATABASE           = "db";
    public static final String OBF_CORE_DATABASE       = "obf-db";
    public static final String CFG_DATABASE            = "conf";
    public static final String ICONS_DIR               = "/icons/";
    // Freedesktop.org-compliant icon theme folder
    public static final String FDO_ICONS_DIR           = "icons";
    public static final String UPDATE_DIR              = "update";
    public static final String UPDATE_VER              = "update_ver";
    public static final String SETTING_UP              = "su";
    public static final String PROFILER                = "profiler";
    public static final String NO_FS_TYPE_CHECK        = "nofstypecheck";
    public static final String SHARED_FOLDER_TAG       = ".aerofs";
    public static final String RECENT_EXCEPTIONS       = "rex";

    /**
     * AuxRoot (auxiliary root) is the location where AeroFS stores temporary, conflict, and history
     * files for a given path that hosts AeroFS physical files.
     *
     * AuxRoot is directly under RootAnchor to ensure they are on the same filesystem.
     */
    public static final String AUXROOT_NAME = ".aerofs.aux";

    /**
     * Seed files are small SQLite dbs used to reduce the impact of aliasing. They are created on
     * unlink and used on the first scan after a reinstall.
     */
    private static final String SEED_FILE_NAME = ".aerofs.seed";

    /**
     * In case user reinstall with a different user account we don't want seed files to be used
     * as it would potentially break sharing (since SID is derived from the OID of the original
     * dir being shared) and may lead to unexpected migration
     * However we do want seed file to be reused in case an external folder is re-joined at the
     * same location, hence we suffix the seed file with the SID
     */
    public static String seedFileName(SID sid)
    {
        return SEED_FILE_NAME + "." + sid.toStringFormal();
    }

    public static enum AuxFolder
    {
        PREFIX("p"),
        CONFLICT("c"),
        REVISION("r");

        /**
         * the base name of the auxiliary folder
         */
        public final String _name;

        private AuxFolder(String name)
        {
            _name = name;
        }
    }

    // This number increments every time the protocol is updated
    public static final int CORE_MAGIC                  = 0x637265C9;
    public static final int CORE_HEADER_LENGTH          = C.INTEGER_SIZE * 2; // (integer magic + integer length)
    public static final int RITUAL_NOTIFICATION_MAGIC   = 0x73209DEF;

    public static final String LOG_FILE_EXT             = ".log";
    public static final String HPROF_FILE_EXT           = ".hprof";

    public static final String GUI_NAME                 = "gui";
    public static final String CLI_NAME                 = "cli";
    public static final String SH_NAME                  = "sh";
    public static final String TOOLS_NAME               = "tools";
    public static final String END_OF_DEFECT_MESSAGE    = "---EOM---";

    public static final InetAddress LOCALHOST_ADDR;
    static {
        InetAddress ia;
        try {
            // don't use "localhost" as some systems (as least Mac) treat it and the
            // numeric address differently
            ia = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            SystemUtil.fatal(e);
            ia = null;
        }
        LOCALHOST_ADDR = ia;
    }

    // Epochs initial values
    public static final int INITIAL_ACL_EPOCH = 0;
    public static final int INITIAL_SYNC_PULL_EPOCH = 0;
    public static final int INITIAL_SYNC_PUSH_EPOCH = 0;


    // Multiuser password: Multiuser installs use certificates to login to servers. Therefore,
    // they do not need a password for remote communication. However, a password is still needed
    // to retrieve private keys locally. (SP and SyncStat server disables password login for
    // multiusers by storing invalid password values.)
    public static final char[] MULTIUSER_LOCAL_PASSWORD = "password".toCharArray();

    public static class Daemon
    {
        public static final long HEARTBEAT_TIMEOUT = 1 * C.MIN; // heartbeats can timeout under load
        public static final long HEARTBEAT_INTERVAL = 5 * C.MIN;
    }

    public static class PostUpdate
    {
        // These variables are saved here rather than *PostUpdateTasks classes so that both
        // UI and processes can access them.
        public static final int DAEMON_POST_UPDATE_TASKS = 35;
        public static final int UI_POST_UPDATE_TASKS = 2;
    }

    public static class Ritual
    {
        public static final int MAX_FRAME_LENGTH = C.MB;
        public static final int LENGTH_FIELD_SIZE = C.INTEGER_SIZE;
    }

    public static class RitualNotification
    {
        public static final long NOTIFICATION_SERVER_CONNECTION_RETRY_INTERVAL = 1 * C.SEC;
    }

    public static class Throttling
    {
        // must be consistent with diagnostics.proto
        public static final long UNLIMITED_BANDWIDTH = 0;
        public static final long MIN_BANDWIDTH_UI = 10 * C.KB;
    }

    public static class Verkehr
    {
        public static final long VERKEHR_RETRY_INTERVAL = 5 * C.SEC;
    }

    public static class SyncStat
    {
        public static final String SS_POST_PARAM_PROTOCOL  = SP.SP_POST_PARAM_PROTOCOL;
        public static final String SS_POST_PARAM_DATA      = SP.SP_POST_PARAM_DATA;
        public static final int SS_PROTOCOL_VERSION         = 6;
    }

    public static class CA
    {
        // TODO (MP) move this to a server-only package (perhaps a new ServerParam.java?)
        public static final URL URL =
                getUrlProperty("base.ca.url", "http://joan.aerofs.com:1029/prod");
    }

    public static class RootAnchor
    {
        public static final Optional<String> DEFAULT_LOCATION_WINDOWS =
                getOptionalStringProperty("lib.anchor.default_location_windows");

        public static final Optional<String> DEFAULT_LOCATION_OSX =
                getOptionalStringProperty("lib.anchor.default_location_osx");

        public static final Optional<String> DEFAULT_LOCATION_LINUX =
                getOptionalStringProperty("lib.anchor.default_location_linux");
    }

    public static class REDIS
    {
        // TODO (MP) rename to lib.redis.address (keeping name for bloomberg compatibility).
        public static final InetSocketAddress ADDRESS = getAddressProperty("sp.redis.address",
                InetSocketAddress.createUnresolved("localhost", 6379));
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
        /** OpenID authentication (if enabled, this replaces credential auth) */
        public static final Boolean                     ENABLED =
                getBooleanProperty(                     "openid.service.enabled", false);

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
                getStringProperty(                      "openid.service.url", "");

        /** The security realm for which we are requesting authorization */
        public static final String                      IDENTITY_REALM =
                getStringProperty(                      "openid.service.realm", "");

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

        /** OpenID discovery may be disabled if YADIS discovery is not supported. */
        public static final Boolean                     DISCOVERY_ENABLED =
                getBooleanProperty(                     "openid.idp.discovery.enabled", false);

        /** Discovery URL for the OpenID provider. Only used if discovery is enabled. */
        public static final String                      DISCOVERY_URL =
                getStringProperty(                      "openid.idp.discovery.url", "");

        /** Endpoint URL used if discovery is not enabled for this OpenID Provider */
        public static final String                      ENDPOINT_URL =
                getStringProperty(                      "openid.idp.endpoint.url", "");

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
                getStringProperty(                      "openid.idp.user.uid.pattern",   "");


        /**
         * Name of the openid extension set to request, or can be empty. Supported extensions are:
         *
         * "ax" for attribute exchange
         *
         * "sreg" for simple registration (an OpenID 1.0 extension)
         */
        public static final String                      IDP_USER_EXTENSION =
                getStringProperty(                      "openid.idp.user.extension", "");

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

    // this class depends on ClientConfigurationLoader
    public static class EnterpriseConfig
    {
        public static Boolean                           IS_ENTERPRISE_DEPLOYMENT =
                getBooleanProperty(                     PROPERTY_IS_ENTERPRISE_DEPLOYMENT,
                                                        false);

        public static final String                      BASE_CA_CERTIFICATE =
                getStringProperty(                      PROPERTY_BASE_CA_CERT,
                                                        "");

        public static final String                      CONFIG_SERVICE_URL =
                getStringProperty(                      PROPERTY_CONFIG_SERVICE_URL,
                                                        "");
}
}

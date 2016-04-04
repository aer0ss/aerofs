/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.C;
import com.aerofs.ids.SID;

import java.net.InetAddress;
import java.util.Optional;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getOptionalStringProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.lib.configuration.ClientConfigurationLoader.PROPERTY_BASE_CA_CERT;

/**
 * Note that Main has dependencies on this class before the configuration is initialized. Hence
 *   this class must not have static fields that are dependent on the configuration subsystem.
 * The subclasses, on the other hand, can have static fields that are dependent on the configuration
 *   subsystem.
 *
 * For more information, Google how Java class loader works regarding static initializers.
 */
public class ClientParam
{
    // This number increments every time the protocol is updated
    public static final int CORE_PROTOCOL_VERSION       = 0x637265D2;
    public static final int RITUAL_NOTIFICATION_MAGIC   = 0x73209DF0;

    // the block size used for content hashing and block storage (see BlockStorage)
    public static final long FILE_BLOCK_SIZE                 = 4 * C.MB;
    public static final long FREQUENT_DEFECT_SENDER_INTERVAL = 30 * C.MIN;

    ////////
    // file and folder names
    public static final String TRASH                   = ".trash$$$";
    public static final String AEROFS_JAR              = "aerofs.jar";
    public static final String PORTBASE                = "pb";
    public static final String NODM                    = "nodm";
    public static final String NOTCP                   = "notcp";
    public static final String NOZEPHYR                = "nozephyr";
    public static final String NOAUTOUPDATE            = "noautoupdate";
    public static final String NOXFF                   = "noxff";
    public static final String LOL                     = "lol";
    public static final String LOLOL                   = "lolol";
    public static final String RECERT                  = "recert";
    public static final String AGGRESSIVE_CHECKS       = "ac";
    public static final String VERSION                 = "version";
    public static final String DEFAULT_RTROOT          = "DEFAULT";
    public static final String DEVICE_CERT             = "cert";
    public static final String DEVICE_KEY_ENCRYPTED    = "key";
    public static final String DEVICE_KEY              = "key.pem";
    public static final String CA_CERT                 = "cacert.pem";
    public static final String CORE_DATABASE           = "db";
    public static final String OBF_CORE_DATABASE       = "obf-db";
    public static final String CFG_DATABASE            = "conf";
    public static final String SA_CFG_FILE             = "storage_agent.conf";
    public static final String ICONS_DIR               = "/icons/";
    // Freedesktop.org-compliant icon theme folder
    public static final String FDO_ICONS_DIR           = "icons";
    public static final String UPDATE_DIR              = "update";
    public static final String UPDATE_VER              = "update_ver";
    public static final String SETTING_UP              = "su";
    public static final String IGNORE_DB_TAMPERING     = "ignoredbtampering";
    public static final String PROFILER                = "profiler";
    public static final String NO_FS_TYPE_CHECK        = "nofstypecheck";
    public static final String SHARED_FOLDER_TAG       = ".aerofs";
    public static final String RECENT_EXCEPTIONS       = "rex";
    // this file is dropped under the new rtroot after rtroot migration to indicate the migration
    // has finished
    public static final String RTROOT_MIGRATION_FINISHED = "rtroot_migrate_finished";
    public static final String FAILED_SID              = "failed_sid";

    /**
     * AuxRoot (auxiliary root) is the location where AeroFS stores temporary, conflict, and history
     * files for a given path that hosts AeroFS physical files.
     *
     * AuxRoot is directly under RootAnchor to ensure they are on the same filesystem.
     */
    public static final String AUXROOT_NAME = ".aerofs.aux";
    /**
     * Default endpoint for the Team Server S3 storage type.
     */
    public static final String DEFAULT_S3_ENDPOINT = "https://s3.amazonaws.com";
    public static final String LOG_FILE_EXT             = ".log";
    public static final String HPROF_FILE_EXT           = ".hprof";
    public static final String GUI_NAME                 = "gui";
    public static final String CLI_NAME                 = "cli";
    public static final String SH_NAME                  = "sh";


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

    public static final int INITIAL_AUDIT_PUSH_EPOCH = 0;
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

    // TODO: move this inside com.aerofs.daemon.core.phy
    public static enum AuxFolder
    {
        PREFIX("p"),
        CONFLICT("c"),
        HISTORY("h"),
        PROBE("probe"),
        NON_REPRESENTABLE("nro"),
        STAGING_AREA("sa");

        /**
         * the base name of the auxiliary folder
         */
        public final String _name;

        private AuxFolder(String name)
        {
            _name = name;
        }
    }

    public static class PostUpdate
    {
        // These variables are saved here rather than *PostUpdateTasks classes so that both
        // UI and processes can access them.
        public static final int DAEMON_POST_UPDATE_TASKS = 68;
        public static final int UI_POST_UPDATE_TASKS = 2;
        public static final int PHOENIX_CONVERSION_TASKS = 5;
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

    public static class RootAnchor
    {
        public static final Optional<String> DEFAULT_LOCATION_WINDOWS =
                getOptionalStringProperty("lib.anchor.default_location_windows");

        public static final Optional<String> DEFAULT_LOCATION_OSX =
                getOptionalStringProperty("lib.anchor.default_location_osx");

        public static final Optional<String> DEFAULT_LOCATION_LINUX =
                getOptionalStringProperty("lib.anchor.default_location_linux");
    }

    /**
     * OpenID and Identity-related configuration that are used by client
     */
    public static class OpenId
    {
        public static boolean enabled()
        {
            return getStringProperty("lib.authenticator", "local_credential").equals("OPENID");
        }

        public static boolean displayUserPassLogin(){return getBooleanProperty("lib.display_user_pass_login", true); }
    }

    // this class depends on ClientConfigurationLoader
    public static class DeploymentConfig
    {
        public static final String                      BASE_CA_CERTIFICATE =
                getStringProperty(                      PROPERTY_BASE_CA_CERT, "");
    }

    public static class ShellextLinkSharing {
        public static Boolean                           IS_ENABLED =
                getBooleanProperty(                     "url_sharing.enabled",
                                                        true);
    }

    public static class Daemon
    {
        public static final long HEARTBEAT_TIMEOUT = 1 * C.MIN; // heartbeats can timeout under load
        public static final long HEARTBEAT_INTERVAL = 5 * C.MIN;

        public static final long CHANNEL_RECONNECT_INITIAL_DELAY = 1 * C.SEC;
        public static final long CHANNEL_RECONNECT_MAX_DELAY = 30 * C.SEC;

        // Both bounds on this range are inclusive, hence the default of 0 to 0 covers listening on port 0 (any port)
        // not final for unit testing
        public static int PORT_RANGE_LOW =
                getIntegerProperty("daemon.port.range.low", 0);

        public static int PORT_RANGE_HIGH =
                getIntegerProperty("daemon.port.range.high", 0);

        public static int PORT_RANGE_FORCE =
                getIntegerProperty("daemon.sa.port.force", 0);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;
import com.aerofs.base.id.SID;
import com.aerofs.config.properties.DynamicOptionalStringProperty;
import com.aerofs.lib.properties.DynamicInetSocketAddress;
import com.aerofs.lib.properties.DynamicUrlProperty;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicStringProperty;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static com.aerofs.lib.configuration.ClientConfigurationLoader.*;

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

    public static final long TRANSPORT_DIAGNOSIS_STATE_PENDING = -1;

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
        // must be consistent with files.proto
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
        public static final DynamicUrlProperty URL =
                new DynamicUrlProperty("base.ca.url", "http://joan.aerofs.com:1029/prod");

        // TODO (MP) Cfg.cacert() should pull from this value, if it exists.
        // If in private deployment mode, this will be populated with the correct certificate on
        // startup when we pull config from the central HTTP configuration source.
        public static final DynamicOptionalStringProperty CERTIFICATE =
                new DynamicOptionalStringProperty("base.ca.certificate");
    }

    public static class RootAnchor
    {
        public static final DynamicOptionalStringProperty DEFAULT_LOCATION_WINDOWS =
                new DynamicOptionalStringProperty("lib.anchor.default_location_windows");

        public static final DynamicOptionalStringProperty DEFAULT_LOCATION_OSX =
                new DynamicOptionalStringProperty("lib.anchor.default_location_osx");

        public static final DynamicOptionalStringProperty DEFAULT_LOCATION_LINUX =
                new DynamicOptionalStringProperty("lib.anchor.default_location_linux");
    }

    public static class REDIS
    {
        // TODO (MP) rename to lib.redis.address (keeping name for bloomberg compatibility).
        public static final DynamicInetSocketAddress ADDRESS =
                new DynamicInetSocketAddress("sp.redis.address",
                        InetSocketAddress.createUnresolved("localhost", 6379));
    }

    // this class depends on ClientConfigurationLoader
    public static class EnterpriseConfig
    {
        public static final DynamicBooleanProperty IS_ENTERPRISE_DEPLOYMENT =
                new DynamicBooleanProperty(PROPERTY_IS_ENTERPRISE_DEPLOYMENT, false);
        public static final DynamicStringProperty ENTERPRISE_CUSTOMER_CERT =
                new DynamicStringProperty(PROPERTY_ENTERPRISE_CUSTOMER_CERT, "");
        public static final DynamicStringProperty CONFIG_SERVICE_URL =
                new DynamicStringProperty(PROPERTY_CONFIG_SERVICE_URL, "");
    }
}

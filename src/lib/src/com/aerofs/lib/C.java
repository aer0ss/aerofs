package com.aerofs.lib;

import java.net.InetAddress;

/**
 * C: constants
 */
public class C
{
    ////////
    // file and folder names

    public static final String TRASH                   = ".trash$$$";
    public static final String AEROFS_JAR              = "aerofs.jar";
    public static final String PORTBASE                = "pb";
    public static final String NODM                    = "nodm";
    public static final String NOSTUN                  = "nostun";
    public static final String NOTCP                   = "notcp";
    public static final String NOXMPP                  = "noxmpp";
    public static final String NOZEPHYR                = "nozephyr";
    public static final String NOAUTOUPDATE            = "noautoupdate";
    public static final String LOL                     = "lol";
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
    public static final String UPDATE_DIR              = "update";
    public static final String UPDATE_VER              = "update_ver";
    public static final String SETTING_UP              = "su";
    public static final String PROFILER                = "profiler";
    public static final String NO_FS_TYPE_CHECK        = "nofstypecheck";
    public static final String NO_OSX_APP_FOLDER_CHECK = "noappfoldercheck";
    public static final String SHARED_FOLDER_TAG       = ".aerofs";
    public static final String LAST_SENT_DEFECT        = "lsd";

    /**
     * AuxRoot (auxiliary root) is the location where AeroFS stores temporary, conflict, and history
     * files for a given path that hosts AeroFS physical files.
     *
     * AuxRoot has the same parent folder as RootAnchor to ensure they are on the same filesystem.
     * AuxRoot's name is AUXROOT_PREFIX + the first 6 characters of the device id, to avoid
     * conflicting AuxRoots in case of multiple installations
     */
    public static final String AUXROOT_PREFIX = ".aerofs.";
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

    ////////
    // everything else

    public static final String SP_POST_PARAM_PROTOCOL  = "protocol_vers";
    public static final String SP_POST_PARAM_DATA      = "data";

    public static final String SS_POST_PARAM_PROTOCOL  = SP_POST_PARAM_PROTOCOL;
    public static final String SS_POST_PARAM_DATA      = SP_POST_PARAM_DATA;

    public static final long SEC = 1000;
    public static final long MIN = 60 * SEC;
    public static final long HOUR = 60 * MIN;
    public static final long DAY = 24 * HOUR;
    public static final long WEEK = 7 * DAY;
    public static final long YEAR = 365 * DAY;

    public static final int KB = 1024;
    public static final int MB = 1024 * KB;
    public static final long GB = 1024 * MB;

    // This number increments every time the protocol is updated
    public static final int CORE_MAGIC                  = 0x637265BF;
    public static final int RITUAL_NOTIFICATION_MAGIC   = 0x73209DEF;

    // Make sure to also update:
    // 1) src/web/development.ini
    // 2) src/web/production.ini
    // 3) syncdet_test/lib/param.py
    // when incrementing SP_PROTOCOL_VERSION
    public static final int SP_PROTOCOL_VERSION         = 18;
    public static final int SS_PROTOCOL_VERSION         = 6;

    public static final String TRANSPORT_ID_XMPP        = "x";
    public static final String TRANSPORT_ID_TCP         = "t";
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

    // Command Server
    public static final String CMD_CHANNEL_TOPIC_PREFIX = "cmd/";

    // Team Server password: Team Servers use certificates to login to servers. Therefore, they do
    // not need a password for remote communication. However, a password is still needed to retrieve
    // private keys locally. (SP  disables password login for team servers by storing invalid
    // password values.)
    public static final char[] TEAM_SERVER_LOCAL_PASSWORD = "password".toCharArray();
}

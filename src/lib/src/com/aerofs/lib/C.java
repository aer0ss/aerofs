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
    public static final String VERSION                 = "version";
    public static final String PID                     = "pid";
    public static final String DEFAULT_RTROOT          = "DEFAULT";
    public static final String STAGING                 = "stg";
    public static final String DEVICE_CERT             = "cert";
    public static final String DEVICE_KEY              = "key";
    public static final String CA_CERT                 = "cacert.pem";
    public static final String CORE_DATABASE           = "db";
    public static final String CFG_DATABASE            = "conf";
    public static final String ICONS_DIR               = "/icons/";
    public static final String UPDATE_DIR              = "update";
    public static final String UPDATE_VER              = "update_ver";
    public static final String SETTING_UP              = "su";
    public static final String PROFILER                = "profiler";
    public static final String NO_FS_TYPE_CHECK        = "nofstypecheck";
    public static final String NO_OSX_APP_FOLDER_CHECK = "noappfoldercheck";
    public static final String LABELING                = "l";
    public static final String SHARED_FOLDER_TAG       = ".aerofs";
    public static final String LAST_SENT_DEFECT        = "lsd";

    /**
     * AUXROOT (auxiliary root) is the location where AeroFS stores temporary, conflict, and history
     * files for a given path that hosts AeroFS physical files. To avoid copying files across
     * filesystems, AUXROOT is always on the same filesystem as the given path. If the path is
     * on the filesystem where RTROOT is located, AUXROOT is equivalent to RTROOT. Otherwise,
     * AUXROOT is �<mount>/.aerofs.aux/<device_id>� where <mount> is the root of the filesystem
     * where the given path is located and <device_id> is the local device ID. For example, Constant
     * AUXROOT_PARENT below defines the parent folder name.
     */
    public static final String AUXROOT_PARENT = ".aerofs.aux";
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

    // The SPServlet associates each user with an organization.
    // This concept isn't useful for "consumers," but their user names must be distinguished from
    // users of the "enterprise" AeroFS. All users of the "consumer" AeroFS will be added
    // to a default organization. For now, the SP Servlet is agnostic to the default organization
    // name.
    public static final String DEFAULT_ORGANIZATION    = "consumer.aerofs.com";

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
    // - whenever changing this number, the major version number also needs to
    //   be incremented (on top of aerofs.daemon/tools/build)
    public static final int CORE_MAGIC                  = 0x637265BB;
    public static final int FSI_MAGIC                   = 0xABCD8422;
    public static final int RITUAL_NOTIFICATION_MAGIC   = 0x73209DEF;

    // Update development.ini and production.ini in java/web when incrementing SP_PROTOCOL_VERSION
    public static final int SP_PROTOCOL_VERSION         = 8;
    public static final int SS_PROTOCOL_VERSION         = 1;

    public static final byte[] ROOT_SID_SALT       = new byte[]
        { (byte) 0x07, (byte) 0x24, (byte) 0xF1, (byte) 0x37 };

    public static final String SUBJECT_ANONYMOUS        = "*";

    public static final String TRANSPORT_ID_XMPP        = "x";
    public static final String TRANSPORT_ID_TCP         = "t";
    public static final String LOG_FILE_EXT             = ".log";

    public static final int EXIT_CODE_EXCEPTION_IN_MAIN      = 22;
    public static final int EXIT_CODE_CANNOT_INIT_LOG4J      = 33;
    public static final int EXIT_CODE_BAD_ARGS               = 44;
    public static final int EXIT_CODE_SHUTDOWN               = 55;
    public static final int EXIT_CODE_FATAL_ERROR            = 66;
    public static final int EXIT_CODE_OUT_OF_MEMORY          = 77;
    public static final int EXIT_CODE_JINGLE_CALL_TOO_LONG   = 88;
    public static final int EXIT_CODE_RELOCATE_ROOT_ANCHOR   = 99;
    public static final int EXIT_CODE_JINGLE_TASK_FATAL_EXIT = 111;

    /// Incorrect S3 access key or secret key for accessing bucket
    public static final int EXIT_CODE_BAD_S3_CREDENTIALS     = 113;

    /// S3 encryption password doesn't match the password used to encrypt the data stored in the bucket
    public static final int EXIT_CODE_BAD_S3_PASSWORD        = 114;

    public static final long STORE_QUOTA_UNLIMITED      = Long.MAX_VALUE;
    public static final long TRANSPORT_DIAGNOSIS_STATE_PENDING = -1;

    public static final int    S3_DELETED_FILE_LEN      = -1;
    public static final String S3_DELETED_FILE_CHUNK_STR = "";
    public static final String GUI_NAME                 = "gui";
    public static final String CLI_NAME                 = "cli";
    public static final String SH_NAME                  = "sh";
    public static final String TOOLS_NAME               = "tools";
    public static final String S3_UPLOADER_NAME         = "s3uploader";
    public static final String S3_CACHE_NAME            = "s3cache";
    public static final String S3_CLEANER_NAME          = "s3cleaner";
    public static final String S3_CACHE_DIR             = "cache";
    public static final String S3_LOCK_DIR              = "lock";
    public static final String S3_CHUNK_DIR             = "chunk";
    public static final String S3_LOCK1_EXT             = ".l";
    public static final String END_OF_DEFECT_MESSAGE    = "---EOM---";

    public static final InetAddress LOCALHOST_ADDR;
    static {
        InetAddress ia;
        try {
            // don't use "localhost" as some systems (as least Mac) treat it and the
            // numeric address differently
            ia = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            Util.fatal(e);
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
}

package com.aerofs.lib;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg;

/**
 * Depends on L, so _do_not_ use this class on any server.
 */
public class S
{
    public static final String
            PREFERENCES              = "Preferences",
            TRANSFERS                = "Transfers",

            BTN_APPLY_UPDATE         = "Apply Update",
            BTN_CHECK_UPDATE         = "Update Now",

            BTN_BACK                 = "Back",
            BTN_CONTINUE             = "Continue",
            BTN_QUIT                 = "Quit",

            LBL_UPDATE_CHECKING      = "Checking for Update...",
            LBL_UPDATE_ONGOING       = "Downloading new update...",
            LBL_UPDATE_LATEST        = "Your " + L.product() + " is up to date.",
            LBL_UPDATE_APPLY         = "A new update has been downloaded.",
            LBL_UPDATE_ERROR         = "An error was encountered.",
            LBL_UPDATE_OUT_OF_DATE   = "Your " + L.product() + " is out of date.\n" +
                    "Please notify your system administrators.",

            // used in setup login screen
            SETUP_TITLE              = "Setup AeroFS Team Server",
            SETUP_MESSAGE            = "Please enter an organization administrator's credential:",
            SETUP_USER_ID            = "Email",
            SETUP_PASSWD             = "Password",
            SETUP_LINK_FORGOT_PASSWD = "<a>Forgot password?</a>",
            SETUP_DEV_ALIAS          = "Computer name",
            SETUP_ERR_CONN           = "Sorry, couldn't connect to the server.",
            SETUP_INSTALL_MESSAGE    = "Setting up",
            SETUP_ERR_RATE_LIMIT     = "Rate limit exceeded.  Wait a minute before trying again.",
            SETUP_ERR_SECOND_FACTOR  = "Incorrect second factor provided",
            SETUP_ERR_SECOND_FACTOR_SETUP = "First, set up your second factor.",
            SETUP_SIGNIN_MESSAGE     = "Signing in",
            SETUP_REGISTERING_MESSAGE = "Registering new device",

            // used in setup storage screen
            SETUP_STORAGE_MESSAGE    = "Where would you like to store your organization's data?",
            SETUP_STORAGE_LOCAL      = "On this computer",
            SETUP_STORAGE_S3         = "On S3-compatible storage\n(Amazon S3, Cloudian)",
            SETUP_STORAGE_SWIFT      = "On Openstack Swift storage",

            // used in setup local storage screen
            ROOT_ANCHOR              = L.isMultiuser() ? "Data Storage folder" :
                    L.product() + " folder",
            SETUP_SELECT_ROOT_ANCHOR = "Select " + ROOT_ANCHOR,
            SETUP_ROOT_ANCHOR_LABEL  = "Storage Location",
            SETUP_USE_DEFAULT        = "Use Default",
            SETUP_TYPE_DESC          = "How would you like to store the data?",
            SETUP_LINK               = "Preserve folder structure",
            SETUP_LINK_DESC          = "Files are accessible directly from the file system.",
            SETUP_BLOCK              = "Compressed and de-duplicated",
            SETUP_BLOCK_DESC         = "Data is accessible only through the shell. It may\n" +
                    "lead to significant space savings.",
            SETUP_STORAGE_LINK       = "<a>Learn more about storage options</a>.",
            SETUP_BTN_INSTALL        = "Install",

            // used in setup Swift storage screen
            SETUP_SWIFT_CONFIG_DESC    = L.brand() + " can store data on OpenStack Swift using basic\n" +
                    "or Keystone authentication. <a>Learn more</a>.",
            SETUP_SWIFT_SELECT_TENANT    = "For Keystone authentication, we can identify the tenant\n" +
                    "by its name or ID. You only need to fill one of the fields.",
            SETUP_SWIFT_KEYSTONE_BETA  = "WARNING: this is an experimental feature and is still in beta.\n" +
                    "Please use it at your own risk.",

            SETUP_SWIFT_SELECT_CONTAINER = "Please enter the name of the container you want to use.\nIf it doesn't exist, we will try to create it.",

            SETUP_SWIFT_URL            = "URL",
            SETUP_SWIFT_AUTH_MODE      = "Authentication Mode",
            SETUP_SWIFT_AUTH_MODE_CLI  = "Authentication Mode ('basic' or 'keystone')",
            SETUP_SWIFT_USERNAME       = "Username",
            SETUP_SWIFT_PASSWORD       = "Password",
            SETUP_SWIFT_CONTAINER      = "Container",
            SETUP_SWIFT_TENANT_ID      = "Tenant ID",
            SETUP_SWIFT_TENANT_NAME    = "Tenant Name",

            SETUP_SWIFT_URL_GUI        = SETUP_SWIFT_URL + ":",
            SETUP_SWIFT_AUTH_MODE_GUI  = SETUP_SWIFT_AUTH_MODE + ":",
            SETUP_SWIFT_USERNAME_GUI   = SETUP_SWIFT_USERNAME + ":",
            SETUP_SWIFT_PASSWORD_GUI   = SETUP_SWIFT_PASSWORD + ":",
            SETUP_SWIFT_CONTAINER_GUI  = SETUP_SWIFT_CONTAINER + ":",
            SETUP_SWIFT_TENANT_ID_GUI  = SETUP_SWIFT_TENANT_ID + ":",
            SETUP_SWIFT_TENANT_NAME_GUI = SETUP_SWIFT_TENANT_NAME + ":",

            // used in setup S3 storage screen
            SETUP_S3_CONFIG_DESC     = "AeroFS supports S3-compatible storage such as Amazon S3,\n" +
                    "and Cloudian. <a>Learn more</a>.",
            SETUP_S3_ENDPOINT_GUI    = "Endpoint:",
            SETUP_S3_BUCKET_NAME_GUI = "Bucket Name:",
            SETUP_S3_ACCESS_KEY_GUI  = "Access Key:",
            SETUP_S3_SECRET_KEY_GUI  = "Secret Key:",

            // Encryption passphrase used in both S3 and Swift
            SETUP_STORAGE_ENC_PASSWD_GUI = "Encryption Passphrase:",
            SETUP_STORAGE_CONF_PASSWD = "Confirm Passphrase:",

            SETUP_S3_ENDPOINT        = "Endpoint",
            SETUP_S3_BUCKET_NAME     = "Bucket name",
            SETUP_S3_ACCESS_KEY      = "Access key",
            SETUP_S3_SECRET_KEY      = "Secret key",

            // Shared strings
            SETUP_STORAGE_PASSWD_DESC = "Please create an encryption passphrase. This passphrase will\n" +
                    "be used to encrypt your data before sending it to the storage\n" +
                    "backend.",
            STORAGE_ENCRYPTION_PASSWORD = "Data encryption passphrase",
            SETUP_STORAGE_ENCRYPTION_PASSWORD = "Create an " + STORAGE_ENCRYPTION_PASSWORD +
                " (used to encrypt your data before sending to the storage backend)",
            SETUP_NOT_ADMIN          = "This account is not an administrator account for the " +
                    "organization. Only administrator accounts can be used to install a Team " +
                    "Server.",

            // Share folder / invite user to shared folder dialog
            SHARE_INVITE_AS          = "Invite as",
            SHARE_PERSONAL_NOTE      = "Personal note (optional):",

            RAW_LOCATION_CHANGE      = "Folder Location Was Changed",

            GUI_LOADING              = "Loading...",

            BAD_CREDENTIAL_CAP       = "Email or password is incorrect",

            OPENID_AUTH_BAD_CRED     = L.product() + " didn't receive a valid authorization from " +
                    "your OpenID Provider.",
            OPENID_AUTH_TIMEOUT      = L.product() + " has timed out while waiting to hear from " +
                    "your OpenID Provider.",

            SERVER_INTERNAL_ERROR    = "Sorry, the " + L.brand() + " servers have encountered " +
                    "an error while processing your request.",

            SETUP_DEFAULT_SIGNIN_ERROR = "Sorry, " + L.product() + " has failed to sign in.",
            SETUP_SIGNIN_PASSWORD_EXPIRED_ERROR = "Sorry, " + L.product() + " has failed to sign in because your" +
                    " password has expired. Please reset your password: " +
                    WWW.PASSWORD_RESET_REQUEST_URL,

            SETUP_DEFAULT_INSTALL_ERROR = "Sorry, " + L.product() + " has failed to install.",

            SETUP_SWIFT_CONNECTION_ERROR = "We weren't able to connect to the Swift node, " +
                    "please make sure the URL and credentials are correct.",
            SETUP_S3_CONNECTION_ERROR = "We weren't able to connect to the S3 bucket, " +
                    "please make sure the URL and credentials are correct, and you have read access to the bucket.",

            MANUAL_REINSTALL = "\nPlease delete \"" + Cfg.absRTRoot() + "\" and restart "
                    + L.product(),
            CORE_DB_TAMPERING = "It looks like the database file may have been tampered with. This"
                    + " can happen when restoring from a backup or using a migration tool.\n\n"
                    + "It is strongly recommended that you reinstall " + L.product() + ".",
            CONFIRM_FORCE_LAUNCH = "Forcing launch may have severe consequences, including missing"
                    + " files and other unpredictable behaviors.",
            FORCE_LAUNCH = "Force Launch",

            INVALID_TOO_LONG                = "Filename too long",
            INVALID_FORBIDDEN_CHARACTERS    = "Forbidden characters",
            INVALID_RESERVED_NAME           = "Reserved filename",
            INVALID_NON_NFC                 = "Non-NFC characters",
            INVALID_TRAILING_SPACE_PERIOD   = "Trailing space or period",

            // transfers dialog

            // N.B. LBL_UNKNOWN_USER & LBL_UNKNOWN_DEVICE should include
            //   custom prefix/suffix as a part of the string
            LBL_UNKNOWN_USER         = "Unknown user's computer",
            LBL_UNKNOWN_DEVICE       = "My unknown computer",
            LBL_UNKNOWN_FILE         = "Incoming file",

            LBL_NO_ACTIVE_TRANSFERS  = "No active transfers",
            LBL_TRANSFERRING         = "transferring files",
            LBL_UPLOADING            = "Uploading",
            LBL_DOWNLOADING          = "Downloading",

            LBL_COL_PATH             = "File",
            LBL_COL_DEVICE           = "From/To",
            LBL_COL_TRANSPORT        = "Connection",
            LBL_COL_PROGRESS         = "Progress",

            LBL_TRANSPORT_TCP        = "LAN",
            LBL_TRANSPORT_ZEPHYR     = "Relay",

            LBL_IDLE                 = "Idle",

            // transport diagnostics dialog
            TXT_TRANSPORT_DIAGNOSTICS_TITLE = "Network Diagnostics...",
            ERR_TRANSPORT_DISABLED   = "The transport is disabled.",
            ERR_GET_TRANSPORTS_INFO_FAILED = "Failed to retrieve network information.",
            LNK_TCP_DESC = "The LAN network consists of computers on the same LAN. " +
                    "These computers discover each other using IP multicast, and then they " +
                    "establish peer-to-peer connections to coordinate and sync. " +
                    "<a>Learn more</a>",
            LNK_ZEPHYR_DESC          = "The computers on the Relay network sync files via an " +
                    "intermediate server acting as a relay. These computers discover each " +
                    "other using a common presence server, and then they communicate over a " +
                    "relay to coordinate and sync. <a>Learn more</a>\n\n" +
                    "The data is encrypted end-to-end, and the relay can't decipher the data. ",
            LBL_ZEPHYR_SERVER_DESC   = "The relay fowards messages from one computer to another.",
            LBL_REACHABLE_DEVICES    = "Reachable Computers:",
            LBL_COL_DEVICE_ID        = "Computer ID",
            LBL_COL_ADDRESS          = "IP Address",
            LBL_COL_USER_ID          = "User ID",
            LBL_COL_DEVICE_NAME      = "Device Name",
            TXT_COLLECTING_NETWORK_INFO = "Collecting network information.",
            LNK_FIND_DEVICE_ID       = "<a>How to find my Computer ID?</a>",

            TEAM_SERVER_USER_ID = "Team Server",

            URL_TRANSPORTS_INFO      = "https://support.aerofs.com/hc/en-us/articles/201439324",
            URL_DEVICE_ID_INFO       = "https://support.aerofs.com/hc/en-us/articles/204592774",

            // preferences dialog
            REPORT_A_PROBLEM = "Report a Problem",
            DEFAULT_DIALOG_TITLE = L.product(),
            DIALOG_TITLE_SUFFIX = " - " + L.product(),

            // invite user to AeroFS dialog
            INVITE_TITLE             = "Invite Coworkers to " + L.brand(),
            INVITE_LBL_INVITE        = L.product() + " is better with coworkers! Email them an " +
                    "invitation.\n\n" +
                    "Email address to invite:",
            INVITE_BTN_INVITE        = "Invite",
            INVITE_STATUS_INVITING   = "Inviting...",

            IMPORTANT_UPDATE_DOWNLOADED =
                "An important update has been downloaded for " + L.product() + ".",
            NO_CONSOLE = "No console is found.",
            COPYRIGHT = "2010-2015 Air Computing Inc. All Rights Reserved.",
            BTN_ADVANCED = "Advanced...",
            BTN_CHANGE = "Change...",
            COULDNT_UNLINK_DEVICE = "Sorry, we could not unlink your computer.",
            UNLINK_THIS_COMPUTER = "Unlink Computer...",
            UNLINK_THIS_COMPUTER_CONFIRM = "Unlink this computer from your " + L.product() +
                " account and quit the program?" +
                " The computer will no longer stay in sync, but will keep files it currently has.",
            CHECKING_FOR_DINOSAURS = "Checking for updates...",
            PRE_SETUP_UPDATE_CHECK_FAILED = L.product() + " couldn't" +
                " download updates. Please make sure the computer is" +
                " connected to the Internet and run " + L.product() + " again.",
            // use trailing spaces to force right margins
            TYPE_EMAIL_ADDRESSES = "Share this folder with:",
            INVITATION_WAS_SENT = "Invited successfully.",
            CLI_NAME = L.productUnixName() + "-cli",
            SH_NAME = L.productUnixName()  + "-sh",
            TRY_AGAIN_LATER = "Please try again later.",
            FAILED_FOR_ACCURACY = "Couldn't retrieve accurate results. " + TRY_AGAIN_LATER,
            COULDNT_LIST_ACTIVITIES = "Couldn't list activities",
            MODIFIED = "updated",

            INVITING = "Inviting...",
            LINKED_DESCRIPTION = "Store files on the local disk",
            LOCAL_DESCRIPTION = "Store compressed files on the local disk",
            S3_DESCRIPTION = "Store files on Amazon S3",
            SWIFT_DESCRIPTION = "Store files on OpenStack Swift",
            USERS_DIR = "users",
            SHARED_DIR = "shared",
            URL_ROLES = "https://support.aerofs.com/hc/en-us/articles/201439384",

            MOBILE_AND_WEB_ACCESS = "mobile and web access",
            URL_API_ACCESS = "https://support.aerofs.com/hc/en-us/articles/202492734",

            SERVER_OFFLINE_TOOLTIP = L.product() + " is offline.",

            CHILD_ALREADY_SHARED = L.product() + " does not support sharing a folder that already " +
                    "contains another shared folder.",
            PARENT_ALREADY_SHARED = L.product() + " does not support sharing a folder under an " +
                    "already shared folder.",
            SIGN_IN_TO_RECERTIFY_ACTION = "To continue syncing files with " + L.product() + " " +
                    "on this device, please sign in to your account now.",
            SIGN_IN_TO_RECERTIFY_EXPLANATION = "(<a>Why is this needed?</a>)",
            ADMIN_EMAIL = "Admin email",
            ADMIN_PASSWD = "Admin password",

            ENABLE_SYNC_HISTORY = "Keep Sync History",
            SYNC_HISTORY_CONFIRM = "Are you sure? Without Sync History, " + L.product()
                    + " cannot restore any files you modify or delete on other devices.",

            NON_OWNER_CANNOT_SHARE = "You don't have the permissions to share this folder with other users.",

            NON_OWNER_CANNOT_CREATE_LINK = "You don't have the permissions to create a link for this file/folder.";
}

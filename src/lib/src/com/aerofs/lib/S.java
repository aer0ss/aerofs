package com.aerofs.lib;

import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.cfg.Cfg;

/*
    N.B. depends on private deployment flag. Do not use before configuration system is initialized.
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

            // used in setup login screen
            SETUP_TITLE              = "Setup AeroFS Team Server",
            SETUP_MESSAGE            = "Please enter an organization administrator's credential:",
            SETUP_USER_ID            = "Email",
            SETUP_PASSWD             = "Password",
            SETUP_LINK_FORGOT_PASSWD = "<a>Forgot password?</a>",
            SETUP_DEV_ALIAS          = "Computer name",
            SETUP_ERR_CONN           = "Sorry, couldn't connect to the server.",
            SETUP_INSTALL_MESSAGE    = "Completing installation",
            SETUP_ERR_RATE_LIMIT     = "Rate limit exceeded.  Wait a minute before trying again.",
            SETUP_ERR_SECOND_FACTOR  = "Incorrect second factor provided",
            SETUP_SIGNIN_MESSAGE     = "Signing in",
            SETUP_REGISTERING_MESSAGE = "Registering new device",

            // used in setup storage screen
            SETUP_STORAGE_MESSAGE    = "Where would you like to store your organization's data?",
            SETUP_STORAGE_LOCAL      = "On this computer",
            SETUP_STORAGE_S3         = "On Amazon S3",

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
            SETUP_BLOCK_DESC         = "Data is accessible only through the shell. " +
                    "It may lead to significant space savings.",
            SETUP_STORAGE_LINK       = "<a>Learn more about storage options</a>.",
            SETUP_BTN_INSTALL        = "Install",

            // used in setup S3 storage screen
            SETUP_S3_CONFIG_DESC     = "If you do not have an Amazon S3 bucket, you may create " +
                    "one at",
            SETUP_S3_AMAZON_LINK     = "<a>http://aws.amazon.com/s3</a>.",
            SETUP_S3_PASSWD_DESC     = "Please create an encryption passphrase. This will be " +
                    "used to encrypt your data before sending it to S3:",
            SETUP_S3_BUCKET_NAME_GUI = "S3 Bucket Name:",
            SETUP_S3_ACCESS_KEY_GUI  = "S3 Access Key:",
            SETUP_S3_SECRET_KEY_GUI  = "S3 Secret Key:",
            SETUP_S3_ENC_PASSWD_GUI  = "Encryption Passphrase:",
            SETUP_S3_CONF_PASSWD     = "Confirm Passphrase:",

            SETUP_S3_BUCKET_NAME     = "S3 bucket name",
            SETUP_S3_ACCESS_KEY      = "S3 access key",
            SETUP_S3_SECRET_KEY      = "S3 secret key",
            S3_ENCRYPTION_PASSWORD   = "S3 data encryption passphrase",
            SETUP_S3_ENCRYPTION_PASSWORD = "Create an " + S3_ENCRYPTION_PASSWORD +
                " (used to encrypt your data before sending to S3)",
            SETUP_NOT_ADMIN          = "This account is not an administrator account for the " +
                    "organization. Only administrator accounts can be used to install a Team " +
                    "Server.",

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
            SETUP_DEFAULT_INSTALL_ERROR = "Sorry, " + L.product() + " has failed to install.",

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
            LBL_TRANSPORT_JINGLE     = "WAN",
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
            LNK_JINGLE_DESC          = "The computers on the WAN network sync files over peer-" +
                    "to-peer connections. These computers discover each other using a common " +
                    "presence server, and then they establish peer-to-peer connection to " +
                    "coordinate and sync. <a>Learn more</a>",
            LBL_XMPP_DESC            = "The presence server allows computers to find each other.",
            LNK_ZEPHYR_DESC          = "The computers on the Relay network sync files via an " +
                    "intermediate server acting as a relay. These computers discover each " +
                    "other using a common presence server, and then they communicate over a " +
                    "relay to coordinate and sync. <a>Learn more</a>\n\n" +
                    "The data is encrypted end-to-end, and the relay can't decipher the data. ",
            LBL_ZEPHYR_SERVER_DESC   = "The relay fowards messages from one computer to another.",
            LBL_REACHABLE_DEVICES    = "Reachable Computers:",
            LBL_COL_DEVICE_ID        = "Computer ID",
            LBL_COL_ADDRESS          = "IP Address",
            TXT_COLLECTING_NETWORK_INFO = "Collecting network information.",
            LNK_FIND_DEVICE_ID       = "<a>How to find my Computer ID?</a>",

            URL_TRANSPORTS_INFO      = "https://support.aerofs.com/entries/25433817",
            URL_DEVICE_ID_INFO       = "https://support.aerofs.com/entries/25449638",

            // preferences dialog
            FILE_OPEN_FAIL = "The file couldn't be opened.",
            CONFLICT_OPEN_FAIL = FILE_OPEN_FAIL +
                " Please use the [Save As...] button to save and view it.",
            REPORT_A_PROBLEM = "Report a Problem",
            DEFAULT_DIALOG_TITLE = L.product(),
            DIALOG_TITLE_SUFFIX = " - " + L.product(),

            IMPORTANT_UPDATE_DOWNLOADED =
                "An important update has been downloaded for " + L.product() + ".",
            NO_CONSOLE = "No console is found.",
            VENDOR = "Air Computing Inc.",
            COPYRIGHT = "2010-2014 " + VENDOR + " All Rights Reserved.",
            BTN_ADVANCED = "Advanced...",
            BTN_CHANGE = "Change...",
            COULDNT_UNLINK_DEVICE = "Sorry, we could not unlink your computer.",
            UNLINK_THIS_COMPUTER = "Unlink Computer...",
            UNLINK_THIS_COMPUTER_CONFIRM = "Unlink this computer from your " + L.product() +
                " account and quit the program?" +
                " The computer will no longer stay in sync, but will keep files it currently has.",
            INVITATION_CODE_NOT_FOUND = "Invitation code not found",
            CHECKING_FOR_DINOSAURS = "Checking for updates...",
            PRE_SETUP_UPDATE_CHECK_FAILED = L.product() + " couldn't" +
                " download updates. Please make sure the computer is" +
                " connected to the Internet and run " + L.product() + " again.",
            // use trailing spaces to force right margins
            TYPE_EMAIL_ADDRESSES = "Enter email addresses here, separated by commas:   ",
            INVITATION_WAS_SENT = "Invited successfully.",
            CLI_NAME = L.productUnixName() + "-cli",
            SH_NAME = L.productUnixName()  + "-sh",
            TRY_AGAIN_LATER = "Please try again later.",
            PASSWORD_CHANGE_INTERNAL_ERROR = "Unable to Login. " + TRY_AGAIN_LATER,
            FAILED_FOR_ACCURACY = "Couldn't retrieve accurate results. " + TRY_AGAIN_LATER,
            COULDNT_LIST_ACTIVITIES = "Couldn't list activities",
            MODIFIED = "updated",

            INVITING = "Inviting...",
            LINKED_DESCRIPTION = "Store files on the local disk",
            LOCAL_DESCRIPTION = "Store compressed files on the local disk",
            S3_DESCRIPTION = "Store files on Amazon S3",
            USERS_DIR = "users",
            SHARED_DIR = "shared",
            URL_ROLES = "https://support.aerofs.com/entries/22831810",

            MOBILE_AND_WEB_ACCESS = "mobile and Web access",
            URL_API_ACCESS = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT ?
                    "https://support.aerofs.com/entries/29044194" :
                    "https://support.aerofs.com/entries/28215600",

            SERVER_OFFLINE_TOOLTIP = L.product() + " is offline.",

            CHILD_ALREADY_SHARED = L.product() + " does not support sharing a folder that already " +
                    "contains another shared folder.\n\n" +
                    "<a href=\"https://support.aerofs.com/hc/en-us/articles/202222050\">" +
                    "Why can't I share a folder containing shared folders</a>?",
            PARENT_ALREADY_SHARED = L.product() + " does not support sharing a folder under an " +
                    "already shared folder.\n\n" +
                    "<a href=\"https://support.aerofs.com/hc/en-us/articles/202222050\">" +
                    "Why can't I share sub-folders of a shared folder</a>?",
            SIGN_IN_TO_RECERTIFY_ACTION = "To continue syncing files with " + L.product() + " " +
                    "on this device, please sign in to your account now.",
            SIGN_IN_TO_RECERTIFY_EXPLANATION = "(<a>Why is this needed?</a>)",
            ADMIN_EMAIL = "Admin email",
            ADMIN_PASSWD = "Admin password",
            PASSWORD_HAS_CHANGED = "Your " + L.product() + " password has changed.\nPlease enter the new password",

            ENABLE_SYNC_HISTORY = "Keep Sync History",
            SYNC_HISTORY_CONFIRM = "Are you sure? Without Sync History, " + L.product()
                    + " cannot restore any files you modify or delete on other devices.",

            NON_OWNER_CANNOT_SHARE = "You don't have the permissions to share this folder with other users.";
}

package com.aerofs.lib;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.labeling.L;

public class S
{
    public static final String
            ERR_CONFIG_SVC_BAD_URL   = "The configuration service URL is not valid!",
            ERR_CONFIG_SVC_CONFIG    = "The configuration service is unavailable!",

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
            SETUP_USER_ID            = "Email",
            SETUP_PASSWD             = "Password",
            SETUP_LINK_FORGOT_PASSWD = "<a>Forgot password?</a>",
            SETUP_DEV_ALIAS          = "Computer name",
            SETUP_TRY_AGAIN          = "Try Again",
            SETUP_ERR_CONN           = "Sorry, couldn't connect to the server. Please try again later.",

            // used in setup storage screen
            SETUP_STORAGE_MESSAGE    = "Where would you like to store your team's data?",
            SETUP_STORAGE_LOCAL      = "On this computer",
            SETUP_STORAGE_S3         = "On Amazon S3",

            // used in setup local storage screen
            ROOT_ANCHOR              = L.isMultiuser() ? "Data Storage folder" :
                    L.product() + " folder",
            SETUP_SELECT_ROOT_ANCHOR = "Select " + ROOT_ANCHOR,
            SETUP_ROOT_ANCHOR_LABEL  = "Storage Location",
            SETUP_USE_DEFAULT        = "Use Default",
            SETUP_TYPE_DESC          = "How would you like to store the data?",
            SETUP_LINK               = "Default",
            SETUP_LINK_DESC          = "Data is readable, writable, and accessible on the file " +
                    "system.",
            SETUP_BLOCK              = "Compressed and de-duplicated",
            SETUP_BLOCK_DESC         = "Data is accessible only through the shell. " +
                    "It may lead to significant space savings.",
            SETUP_BTN_INSTALL        = "Install",

            // used in setup S3 storage screen
            SETUP_S3_CONFIG_DESC     = "If you do not have an Amazon S3 bucket, you may create " +
                    "one at",
            SETUP_S3_AMAZON_LINK     = "<a>http://aws.amazon.com/s3</a>.",
            SETUP_S3_AMAZON_URL      = "http://aws.amazon.com/s3",
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

            RAW_LOCATION_CHANGE      = "Folder Location Was Changed",

            GUI_LOADING              = "Loading...",

            BAD_CREDENTIAL_CAP       = "Email or password is incorrect",

            // begin transfers dialog
            LBL_UNKNOWN_USER         = "Unknown user",
            LBL_UNKNOWN_DEVICE       = "Unknown device",
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
            // end transfers dialog

            DONT_SHOW_THIS_MESSAGE_AGAIN = "Don't show me again.",
            FILE_OPEN_FAIL = "The file couldn't be opened.",
            CONFLICT_OPEN_FAIL = FILE_OPEN_FAIL +
                " Please use the [Save As...] button to save and view it.",
            WHY_ARENT_MY_FILES_SYNCED = "Why Aren't My Files Synced?",
            REPORT_A_PROBLEM = "Report a Problem",
            REQUEST_A_FEATURE = "Request a Feature",
            DEFAULT_DIALOG_TITLE = L.product(),
            DIALOG_TITLE_SUFFIX = " - " + L.product(),

            IMPORTANT_UPDATE_DOWNLOADED =
                "An important update has been downloaded for " + L.product() + ".",
            NO_CONSOLE = "No console is found.",
            VENDOR = "Air Computing Inc.",
            COPYRIGHT = "2010-2013 " + VENDOR + " All Rights Reserved.",
            BTN_ADVANCED = "Advanced...",
            BTN_CHANGE = "Change...",
            COULDNT_UNLINK_DEVICE = "Sorry, we could not unlink your computer.",
            UNLINK_THIS_COMPUTER = "Unlink This Computer...",
            UNLINK_THIS_COMPUTER_CONFIRM = "Unlink this computer from your " + L.product() +
                " account and quit the program?" +
                " The computer will no longer stay in sync, but will keep files it currently has.",
            INVITATION_CODE_NOT_FOUND = "Invitation code not found",
            CHECKING_FOR_DINOSAURS = "Checking for dinosaurs...",
            PRE_SETUP_UPDATE_CHECK_FAILED = L.product() + " couldn't" +
                " download updates. Please make sure the computer is" +
                " connected to the Internet and run " + L.product() + " again.",
            PASSWORD_RESET_URL = WWW.DASHBOARD_HOST_URL.get() + "/password_reset",
            // use trailing spaces to force right margins
            TYPE_EMAIL_ADDRESSES = "Enter email addresses here, separated by commas:   ",
            INVITATION_WAS_SENT = "Invited successfully.",
            COULDNT_SEND_INVITATION = "Couldn't invite users.",
            CLI_NAME = L.productUnixName() + "-cli",
            SH_NAME = L.productUnixName()  + "-sh",
            TRY_AGAIN_LATER = "Please try again later.",
            PASSWORD_CHANGE_INTERNAL_ERROR = "Unable to Login. " + TRY_AGAIN_LATER,
            FAILED_FOR_ACCURACY = "Couldn't retrieve accurate results. " + TRY_AGAIN_LATER,
            COULDNT_LIST_ACTIVITIES = "Couldn't list activities",
            MODIFIED = "updated",

            SYNC_STATUS_DOWN = "Sync status is temporarily unavailable.",
            SYNC_STATUS_LOCAL = "Not synced with other devices.",
            SS_IN_SYNC_TOOLTIP = "Remote peer has the same version.",
            SS_IN_PROGRESS_TOOLTIP = "Remote peer has a different version and is currently" +
                    " online. This may happen if the remote peer is using selective sync.",
            SS_OFFLINE_TOOLTIP = "Remote peer has a different version and is currently offline.",
            INVITING = "Inviting...",
            LINKED_DESCRIPTION = "Store files on the local disk",
            LOCAL_DESCRIPTION = "Store compressed files on the local disk",
            S3_DESCRIPTION = "Store files on Amazon S3",
            USERS_DIR = "users",
            SHARED_DIR = "shared",

            CHILD_ALREADY_SHARED = "You can't share a folder that contains a shared folder.",
            PARENT_ALREADY_SHARED = "You can't share a folder under an already shared folder.",
            TYPE_ADMIN_PASSWORD_TO_RECERTIFY_TEAM_SERVER = "We've made a security update that " +
                    "requires you to enter an admin's credentials on this Team Server. " +
                    "Once you enter the password, the Team Server will shutdown, and you will need" +
                    " to manually restart it. We apologize for the inconvenience. " +
                    "Email " +
                    WWW.SUPPORT_EMAIL_ADDRESS.get() + " if you have questions.",
            ADMIN_EMAIL = "Admin email",
            ADMIN_PASSWD = "Admin password",
            PASSWORD_HAS_CHANGED = "Your " + L.product() + " password has changed.\nPlease enter the new password",

            ENABLE_SYNC_HISTORY = "Keep Sync History on this device",
            SYNC_HISTORY_CONFIRM = "Are you sure? Without Sync History, " + L.product()
                    + " cannot restore any files you modify or delete on other devices.";
}

package com.aerofs.lib;

import com.aerofs.labeling.L;
import com.aerofs.base.BaseParam.SP;

public class S
{
    public static final String
            PREFERENCES              = "Preferences",
            TRANSFERS                = "Transfers",

            BTN_APPLY_UPDATE         = "Apply Update",
            BTN_CHECK_UPDATE         = "Update Now",

            LBL_UPDATE_CHECKING      = "Checking for Update...",
            LBL_UPDATE_ONGOING       = "Downloading new update...",
            LBL_UPDATE_LATEST        = "Your " + L.PRODUCT + " is up to date.",
            LBL_UPDATE_APPLY         = "A new update has been downloaded.",
            LBL_UPDATE_ERROR         = "An error was encountered.",

            SETUP_USER_ID            = "Email",
            SETUP_PASSWD             = "Password",
            SETUP_DEV_ALIAS          = "Computer name",
            ROOT_ANCHOR              = L.get().isMultiuser() ? "Data Storage folder" :
                L.PRODUCT + " folder",

            SETUP_S3                 = "Do you wish to set up this client to use Amazon S3 for " +
                "storage (EXPERIMENTAL)?",
            SETUP_S3_BUCKET_NAME     = "S3 bucket name",
            SETUP_S3_ACCESS_KEY      = "S3 access key",
            SETUP_S3_SECRET_KEY      = "S3 secret key",
            S3_ENCRYPTION_PASSWORD   = "S3 data encryption passphrase",
            SETUP_S3_ENCRYPTION_PASSWORD = "Create an " + S3_ENCRYPTION_PASSWORD +
                " (used to encrypt your data before sending to S3)",

            RAW_LOCATION_CHANGE      = "Folder Location Was Changed",

            GUI_LOADING              = "Loading...",

            BAD_CREDENTIAL_CAP       = "Username/password combination is incorrect",

            DONT_SHOW_THIS_MESSAGE_AGAIN = "Don't show me again.",
            FILE_OPEN_FAIL = "The file couldn't be opened.",
            CONFLICT_OPEN_FAIL = FILE_OPEN_FAIL +
                " Please use the [Save As...] button to save and view it.",
            WHY_ARENT_MY_FILES_SYNCED = "Why Aren't My Files Synced?",
            REPORT_A_PROBLEM = "Report a Problem",
            REQUEST_A_FEATURE = "Request a Feature",
            DEFAULT_DIALOG_TITLE = L.PRODUCT,
            DIALOG_TITLE_SUFFIX = " - " + L.PRODUCT,

            IMPORTANT_UPDATE_DOWNLOADED =
                "An important update has been downloaded for " + L.PRODUCT + ".",
            NO_CONSOLE = "No console is found.",
            VENDOR = "Air Computing Inc.",
            COPYRIGHT = "2010-2013 " + VENDOR + " All Rights Reserved.",
            BTN_ADVANCED = "Advanced...",
            BTN_CHANGE = "Change...",
            UNLINK_THIS_COMPUTER = "Unlink This Computer...",
            UNLINK_THIS_COMPUTER_CONFIRM = "Unlink this computer from the " +
                L.PRODUCT + " account and quit the program?" +
                " Files in the " + L.PRODUCT + " folder will not be deleted.",
            INVITATION_CODE_NOT_FOUND = "Invitation code not found",
            CHECKING_FOR_DINOSAURS = "Checking for dinosaurs...",
            PRE_SETUP_UPDATE_CHECK_FAILED = L.PRODUCT + " couldn't" +
                " download updates. Please make sure the computer is" +
                " connected to the Internet and run " + L.PRODUCT + " again.",
            PRIVACY_URL = SP.WEB_BASE + "/privacy",
            PASSWORD_RESET_REQUEST_URL = SP.DASH_BOARD_BASE + "/request_password_reset",
            PASSWORD_RESET_URL = SP.DASH_BOARD_BASE + "/password_reset",
            // use trailing spaces to force right margins
            TYPE_EMAIL_ADDRESSES = "Enter email addresses here, separated by commas:   ",
            INVITATION_WAS_SENT = "Invited successfully.",
            COULDNT_SEND_INVITATION = "Couldn't invite users.",
            VERSION_HISTORY = "version history",
            CLI_NAME = L.get().productUnixName() + "-cli",
            TRY_AGAIN_LATER = "Please try again later.",
            PASSWORD_CHANGE_INTERNAL_ERROR = "Unable to Login. " +
            TRY_AGAIN_LATER,
            FAILED_FOR_ACCURACY = "Couldn't retrieve accurate results. " +
            TRY_AGAIN_LATER,
            COULDNT_LIST_ACTIVITIES = "Couldn't list activities",
            MODIFIED = "updated",

            SYNC_STATUS_DOWN = "Sync status is temporarily unavailable.",
            SYNC_STATUS_LOCAL = "Not synced with other devices.",
            SS_IN_SYNC_TOOLTIP = "Remote peer has the same version.",
            SS_IN_PROGRESS_TOOLTIP = "Remote peer has a different version and is currently" +
                    " online. This may happen if the remote peer is using selective sync.",
            SS_OFFLINE_TOOLTIP = "Remote peer has a different version and is currently offline.",
            INVITING = "Inviting...";
}

package com.aerofs.lib;

import com.aerofs.lib.Param.SP;

public class S
{
    public static final String PRODUCT                  = L.get().product();
    public static final String TEAM_SERVER              = "Team Server";
    public static final String TEAM_SERVERS             = "Team Servers";
    public static final String PREFERENCES              = "Preferences";
    public static final String TRANSFERS                = "Transfers";

    public static final String TERMS_OF_SERVICE         = "Terms of Service";

    public static final String BTN_APPLY_UPDATE         = "Apply Update";
    public static final String BTN_CHECK_UPDATE         = "Update Now";

    public static final String LBL_UPDATE_CHECKING      = "Checking for Update...";
    public static final String LBL_UPDATE_ONGOING       = "Downloading new update...";
    public static final String LBL_UPDATE_LATEST        = "Your " + PRODUCT + " is up to date.";
    public static final String LBL_UPDATE_APPLY         = "A new update has been downloaded.";
    public static final String LBL_UPDATE_ERROR         = "An error was encountered.";

    public static final String SETUP_USER_ID            = "Email address";
    public static final String SETUP_PASSWD             = "Password";
    public static final String SETUP_FIRST_NAME         = "First name";
    public static final String SETUP_LAST_NAME          = "Last name";
    public static final String SETUP_DEV_ALIAS          = "Computer name";
    public static final String SETUP_ANCHOR_ROOT        = PRODUCT + " folder to sync";
    public static final String SETUP_IC                 = "Invitation code";
    public static final String SETUP_CANT_VERIFY_IIC    = "Couldn't verify invitation code ";
    public static final String SETUP_INVALID_USER_ID    = "Email address is not valid.";
    public static final String SETUP_PASSWD_TOO_SHORT   = "Password is too short.";
    public static final String SETUP_PASSWD_INVALID     = "Password must contain ASCII letters only.";
    public static final String SETUP_PASSWD_DONT_MATCH  = "Passwords do not match.";
    public static final String SETUP_RETYPE_PASSWD      = "Retype password";
    public static final String SETUP_I_AGREE_TO_THE     = "I agree to the";

    public static final String SETUP_S3                 =
            "Do you wish to set up this client to use Amazon S3 for storage (EXPERIMENTAL)?";
    public static final String SETUP_S3_BUCKET_NAME     = "S3 bucket name";
    public static final String SETUP_S3_ACCESS_KEY      = "S3 access key";
    public static final String SETUP_S3_SECRET_KEY      = "S3 secret key";
    public static final String S3_ENCRYPTION_PASSWORD   = "S3 data encryption password";
    public static final String SETUP_S3_ENCRYPTION_PASSWORD =
            "Create an " + S3_ENCRYPTION_PASSWORD + " (used to encrypt your data before sending to S3)";

    public static final String ROOT_ANCHOR_NAME         = PRODUCT;

    public static final String RAW_LOCATION_CHANGE      = PRODUCT + " Has Changed Location";

    public static final String GUI_LOADING              = "Loading...";

    public static final String BAD_CREDENTIAL_CAP       = "Username/password combination is incorrect";

    public static final String DONT_SHOW_THIS_MESSAGE_AGAIN = "Don't show me again.";
    public static final String FILE_OPEN_FAIL = "The file couldn't be opened.";
    public static final String CONFLICT_OPEN_FAIL = FILE_OPEN_FAIL +
            " Please use the [Save As...] button to save and view it.";
    public static final String WHY_ARENT_MY_FILES_SYNCED = "Why Aren't My Files Synced?";
    public static final String REPORT_A_PROBLEM = "Report a Problem";
    public static final String REQUEST_A_FEATURE = "Request a Feature";
    public final static String DEFAULT_DIALOG_TITLE = PRODUCT;
    public final static String DIALOG_TITLE_SUFFIX = " - " + PRODUCT;

    public final static String IMPORTANT_UPDATE_DOWNLOADED =
        "An important update has been downloaded for " + S.PRODUCT + ".";
    public final static String NO_CONSOLE = "No console is found.";
    public static final String COPYRIGHT = "2010-2012 " + L.get().vendor() + " All Rights Reserved.";
    public static final String BTN_ADVANCED = "Advanced...";
    public static final String BTN_CHANGE = "Change...";
    public static final String UNLINK_THIS_COMPUTER_CONFIRM = "Unlink this computer from the " +
        S.PRODUCT + " account and quit the program?" +
        " Files in the " + S.PRODUCT + " folder will not be deleted.";
    public static final String INVITATION_CODE_NOT_FOUND = "Invitation code not found";
    public static final String CHECKING_FOR_DINOSAURS = "Checking for dinosaurs...";
    public static final String PRE_SETUP_UPDATE_CHECK_FAILED = S.PRODUCT + " couldn't" +
        " download updates. Please make sure the computer is" +
        " connected to the Internet and run " + S.PRODUCT + " again.";
    public static final String PRIVACY_URL = SP.WEB_BASE + "privacy";
    public static final String TOS_URL = SP.WEB_BASE + "tos";
    public static final String PASSWORD_RESET_REQUEST_URL = SP.WEB_BASE + "request_password_reset";
    public static final String PASSWORD_RESET_URL = SP.WEB_BASE + "password_reset";
    // use trailing spaces to force right margins
    public static final String TYPE_EMAIL_ADDRESSES = "Enter email addresses here, separated by commas:   ";
    public static final String SENDING_INVITATION = "Inviting";
    public static final String INVITATION_WAS_SENT = "Invited successfully.";
    public static final String COULDNT_SEND_INVITATION = "Couldn't invite users.";
    public static final String VERSION_HISTORY = "version history";
    public static final String CLI_NAME = L.get().productUnixName() + "-cli";
    public static final String TRY_AGAIN_LATER = "Please try again later.";
    public static final String PASSWORD_CHANGE_INTERNAL_ERROR = "Unable to Login. " +
            TRY_AGAIN_LATER;
    public static final String FAILED_FOR_ACCURACY = "Couldn't retrieve accurate results. " +
            TRY_AGAIN_LATER;
    public static final String COULDNT_LIST_ACTIVITIES = "Couldn't list activities";
    public static final String MODIFIED = "updated";

    public static final String SYNC_STATUS_DOWN = "Sync status is temporarily unavailable.";
    public static final String SYNC_STATUS_LOCAL = "Not synced with other devices.";

    public static final String
            SS_IN_SYNC_TOOLTIP      = "Remote peer has the same version.",
            SS_IN_PROGRESS_TOOLTIP  = "Remote peer has a different version and is currently online. " +
                    "This may happen if the remote peer is using selective sync.",
            SS_OFFLINE_TOOLTIP      = "Remote peer has a different version and is currently offline.";

}

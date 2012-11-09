package com.aerofs.cli;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Properties;

import com.aerofs.lib.Base64;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Param;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.aerofs.proto.Sv;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;

public class CLISetup
{
    /**
     * File containing settings for unattended setup.
     *
     * If a file with this name exists in the runtime root, the CLI will
     * use the values therein for running the setup procedure instead of asking the
     * user interactively.
     */
    private static final String UNATTENDED_SETUP_FILE = "unattended-setup.properties";

    /*
        # Example file contents
        userid = test@aerofs.com
        password = password
        first_name = John
        last_name = Smith
        device = Ye Olde MacBook Pro
     */

    private static final String
            PROP_USERID = "userid",
            PROP_PASSWORD = "password",
            PROP_DEVICE = "device",
            PROP_ROOT = "root",
            PROP_INVITE = "invite",
            PROP_FIRST_NAME = "first_name",
            PROP_LAST_NAME = "last_name";

    private boolean _isExistingUser;

    private String getUser(CLI cli) throws ExNoConsole
    {
        // input user name - keep going as long as it's invalid
        while (true) {
            String user = cli.askText(S.SETUP_USER_ID, null);
            if (!Util.isValidEmailAddress(user)) {
                cli.show(MessageType.ERROR, S.SETUP_INVALID_USER_ID);
            } else {
                return user;
            }
        }
    }

    CLISetup(CLI cli, String rtRoot) throws Exception
    {
        String userID = null;
        char[] passwd = null;

        GetSetupSettingsReply defaults = UI.controller().getSetupSettings();
        String deviceName = defaults.getDeviceName();
        String anchorRoot = defaults.getRootAnchor();

        String signUpCode = null;
        String firstName = null;
        String lastName = null;

        String s3BucketId = null;
        String s3AccessKey = null;
        String s3SecretKey = null;
        char[] s3EncryptionPassword = null;
        String s3LocalDir = null;

        File rtRootFile = new File(rtRoot);
        File setupFile = new File(rtRootFile, UNATTENDED_SETUP_FILE);

        if (setupFile.exists()) {
            Properties props = new Properties();
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(setupFile));
            try {
                props.load(in);
            } finally {
                in.close();
            }

            signUpCode = props.getProperty(PROP_INVITE);

            if (signUpCode != null) {
                userID = UI.controller().resolveSignUpCode(signUpCode).getEmail();
            }
            if (userID == null || userID.isEmpty()) userID = props.getProperty(PROP_USERID);
            passwd = props.getProperty(PROP_PASSWORD).toCharArray();
            anchorRoot = props.getProperty(PROP_ROOT, anchorRoot);
            deviceName = props.getProperty(PROP_DEVICE, deviceName);

            _isExistingUser = (signUpCode == null);
            if (!_isExistingUser) {
                FullName defaultName = UIUtil.getDefaultFullName();
                firstName = props.getProperty(PROP_FIRST_NAME, defaultName._first);
                lastName = props.getProperty(PROP_LAST_NAME, defaultName._last);
            }

            s3BucketId = props.getProperty(CfgDatabase.Key.S3_BUCKET_ID.keyString());
            if (s3BucketId != null) {
                s3AccessKey = props.getProperty(CfgDatabase.Key.S3_ACCESS_KEY.keyString());
                s3SecretKey = props.getProperty(CfgDatabase.Key.S3_SECRET_KEY.keyString());
                s3EncryptionPassword = props.getProperty(
                        CfgDatabase.Key.S3_ENCRYPTION_PASSWORD.keyString()).toCharArray();
                s3LocalDir = props.getProperty(CfgDatabase.Key.S3_DIR.keyString());
            }

        } else {
            if (!OSUtil.isLinux()) {
                cli.confirm(MessageType.WARN, S.PRODUCT + " CLI is not officially supported" +
                        " on non-Linux platforms. Specifically, GUI will be started" +
                        " up on the next automatic update.");
            }

            _isExistingUser = cli.ask(MessageType.INFO, "Welcome! Do you have an " + S.PRODUCT +
                    " account already?");
            if (_isExistingUser) {
                userID = getUser(cli);
                cli.show(MessageType.INFO, "If you forgot your password, go to " +
                        S.PASSWORD_RESET_REQUEST_URL + " to reset it.");
                passwd = cli.askPasswd(S.SETUP_PASSWD);

            } else {
                // input invitation code
                while (true) {
                    signUpCode = cli.askText(S.SETUP_IC, null);
                    try {
                        cli.progress("Verifying invitation code");
                        userID = UI.controller().resolveSignUpCode(signUpCode).getEmail();
                        if (!userID.isEmpty()) {
                            cli.show(MessageType.INFO, S.SETUP_USER_ID + ": " + userID);
                        } else {
                            userID = getUser(cli);
                        }
                        break;
                    } catch (Exception e) {
                        cli.show(MessageType.ERROR, S.SETUP_CANT_VERIFY_IIC + UIUtil.e2msg(e));
                    }
                }

                // input passwd
                passwd = inputAndConfirmPasswd(cli, S.SETUP_PASSWD);

                // input user name
                FullName defaultName = UIUtil.getDefaultFullName();
                while (firstName == null || firstName.isEmpty()) {
                    firstName = cli.askText(S.SETUP_FIRST_NAME, defaultName._first);
                }
                while (lastName == null || lastName.isEmpty()) {
                    lastName = cli.askText(S.SETUP_LAST_NAME, defaultName._last);
                }
            }

            // input device name
            deviceName = cli.askText(S.SETUP_DEV_ALIAS, deviceName);

            // input S3 info
            if (cli.ask(MessageType.INFO, S.SETUP_S3)) {
                while (s3BucketId == null || s3BucketId.isEmpty()) {
                    s3BucketId = cli.askText(S.SETUP_S3_BUCKET_NAME, null);
                }
                while (s3AccessKey == null || s3AccessKey.isEmpty()) {
                    s3AccessKey = cli.askText(S.SETUP_S3_ACCESS_KEY, null);
                }
                while (s3SecretKey == null || s3SecretKey.isEmpty()) {
                    s3SecretKey = cli.askText(S.SETUP_S3_SECRET_KEY, null);
                }
                while (s3EncryptionPassword == null || s3EncryptionPassword.length == 0) {
                    s3EncryptionPassword = inputAndConfirmPasswd(cli,
                            S.SETUP_S3_ENCRYPTION_PASSWORD);
                }

            } else {
                // input anchor root
                String input = cli.askText(S.SETUP_ANCHOR_ROOT, anchorRoot);
                String root = RootAnchorUtil.adjustRootAnchor(input);
                if (!input.equals(root)) {
                    cli.confirm(MessageType.INFO,
                            "The path has been adjusted to " + Util.q(root) + ".");
                }
                anchorRoot = root;
            }

            if (!_isExistingUser) {
                // tos
                if (!cli.ask(MessageType.INFO, S.SETUP_I_AGREE_TO_THE + " " + S.TERMS_OF_SERVICE +
                        " (" + S.TOS_URL + ")")) {
                    throw new ExAborted();
                }
            }
        }

        PBS3Config s3config = null;
        if (s3BucketId != null) {
            if (s3LocalDir == null) s3LocalDir = new File(Cfg.absRTRoot(), "s3").getPath();
            File dir = new File(s3LocalDir);
            if (!dir.isDirectory()) FileUtil.mkdirs(dir);

            String scrypted = Base64.encodeBytes(SecUtil.scrypt(s3EncryptionPassword, userID));

            s3config = PBS3Config.newBuilder()
                .setBucketId(s3BucketId)
                .setAccessKey(s3AccessKey)
                .setSecretKey(s3SecretKey)
                .setEncryptionKey(scrypted)
                .setLocalDir(s3LocalDir)
                .build();
        }

        cli.progress("Performing magic");

        if (_isExistingUser) {
            UI.controller().setupExistingUser(userID, new String(passwd), anchorRoot,
                    deviceName, s3config);
        } else {
            UI.controller().setupNewUser(userID, new String(passwd), anchorRoot, deviceName,
                    signUpCode, firstName, lastName, s3config);
        }

        if (s3BucketId != null) SVClient.sendEventAsync(Sv.PBSVEvent.Type.S3_SETUP);

        cli.notify(MessageType.INFO,
                "+-------------------------------------------------+\n" +
                "| You can now access " + S.PRODUCT + " functions through the |\n" +
                "| " + Util.q("aerofs-sh") + " command while aerofs-cli is running |\n" +
                "+-------------------------------------------------+");
    }

    public boolean isExistingUser()
    {
         return _isExistingUser;
    }

    private char[] inputAndConfirmPasswd(CLI cli, String prompt) throws ExNoConsole
    {
        while (true) {
            char[] passwd = cli.askPasswd(prompt);
            if (passwd.length < Param.MIN_PASSWD_LENGTH) {
                cli.show(MessageType.ERROR, S.SETUP_PASSWD_TOO_SHORT);
            } else if (!Util.isValidPassword(passwd)) {
                cli.show(MessageType.ERROR, S.SETUP_PASSWD_INVALID);
            } else {
                char[] passwd2 = cli.askPasswd(S.SETUP_RETYPE_PASSWD);
                if (!Arrays.equals(passwd, passwd2)) {
                    cli.show(MessageType.ERROR, S.SETUP_PASSWD_DONT_MATCH);
                } else {
                    return passwd;
                }
            }
        }
    }
}

package com.aerofs.cli;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.labeling.L;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.PasswordVerifier;
import com.aerofs.ui.PasswordVerifier.PasswordVerifierResult;
import com.aerofs.ui.UI;

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
            PROP_ROOT = "root";

    private boolean _isUnattendedSetup;

    private UserID _userID = null;
    private char[] _passwd;
    private String _anchorRoot = null;
    private String _deviceName = null;
    private PBS3Config _s3config = null;

    CLISetup(CLI cli, String rtRoot) throws Exception
    {
        GetSetupSettingsReply defaults = UI.controller().getSetupSettings();
        _deviceName = defaults.getDeviceName();
        _anchorRoot = defaults.getRootAnchor();

        processSetupFile(rtRoot);

        if (!OSUtil.isLinux() && !_isUnattendedSetup) {
            cli.confirm(MessageType.WARN, L.PRODUCT + " CLI is not officially supported" +
                    " on non-Linux platforms. Specifically, GUI will be started" +
                    " up on the next automatic update.");
        }

        cli.show(MessageType.INFO, "Welcome to " + L.PRODUCT + ".");

        if (L.get().isMultiuser()) {
            setupMultiuser(cli);
        } else {
            setupSingleuser(cli);
        }

        cli.notify(MessageType.INFO,
                "---------------------------------------------------------------\n" +
                "You can now access " + L.PRODUCT + " functions through the\n" +
                Util.quote(S.SH_NAME) + " command while " +
                S.CLI_NAME + " is running.");
    }

    private void processSetupFile(String rtRoot) throws Exception
    {
        String s3BucketId;
        String s3AccessKey = null;
        String s3SecretKey = null;
        char[] s3EncryptionPassword = null;

        File rtRootFile = new File(rtRoot);
        File setupFile = new File(rtRootFile, UNATTENDED_SETUP_FILE);

        if (!setupFile.exists()) return;

        Properties props = new Properties();
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(setupFile));
        try {
            props.load(in);
        } finally {
            in.close();
        }

        if (_userID == null) _userID = UserID.fromExternal(props.getProperty(PROP_USERID));

        _passwd = props.getProperty(PROP_PASSWORD).toCharArray();
        _anchorRoot = props.getProperty(PROP_ROOT, _anchorRoot);
        _deviceName = props.getProperty(PROP_DEVICE, _deviceName);

        s3BucketId = props.getProperty(CfgDatabase.Key.S3_BUCKET_ID.keyString());
        if (s3BucketId != null) {
            s3AccessKey = props.getProperty(CfgDatabase.Key.S3_ACCESS_KEY.keyString());
            s3SecretKey = props.getProperty(CfgDatabase.Key.S3_SECRET_KEY.keyString());
            s3EncryptionPassword = props.getProperty(
                    CfgDatabase.Key.S3_ENCRYPTION_PASSWORD.keyString()).toCharArray();
        }

        if (s3BucketId != null) {
            String scrypted = Base64.encodeBytes(SecUtil.scrypt(s3EncryptionPassword, _userID));

            _s3config = PBS3Config.newBuilder()
                    .setBucketId(s3BucketId)
                    .setAccessKey(s3AccessKey)
                    .setSecretKey(s3SecretKey)
                    .setEncryptionKey(scrypted)
                    .build();
        }

        _isUnattendedSetup = true;
    }

    private void setupMultiuser(CLI cli) throws Exception
    {
        if (!_isUnattendedSetup) {
            getUser(cli);
            getPassword(cli);
            getDeviceName(cli);
            if (cli.ask(MessageType.INFO, S.SETUP_S3)) {
                getS3Config(cli);
            } else {
                getRootAnchor(cli);
            }
        }

        cli.progress("Performing magic");

        // TODO: support LINKED in cli setup
        StorageType storageType = _s3config != null ? StorageType.S3 : StorageType.LOCAL;

        UI.controller().setupMultiuser(_userID.getString(), new String(_passwd), _anchorRoot,
                _deviceName, storageType.name(), _s3config);
    }

    private void setupSingleuser(CLI cli) throws Exception
    {
        if (!_isUnattendedSetup) {
            getUser(cli);
            getPassword(cli);
            getDeviceName(cli);
            getRootAnchor(cli);
        }

        cli.progress("Performing magic");

        // TODO: support other backends?
        StorageType storageType = StorageType.LINKED;

        UI.controller().setupSingleuser(_userID.getString(), new String(_passwd), _anchorRoot,
                _deviceName, storageType.name(), null);
    }

    private void getUser(CLI cli)
            throws ExNoConsole, ExEmptyEmailAddress
    {
        _userID = UserID.fromExternal(cli.askText(S.SETUP_USER_ID, null));
    }

    private void getPassword(CLI cli) throws Exception
    {
        cli.show(MessageType.INFO, "If you forgot your password, go to " +
                WWW.PASSWORD_RESET_REQUEST_URL + " to reset it.");
        _passwd =  cli.askPasswd(S.SETUP_PASSWD);
    }

    private void getRootAnchor(CLI cli) throws Exception
    {
        String input = cli.askText(S.ROOT_ANCHOR, _anchorRoot);
        String root = RootAnchorUtil.adjustRootAnchor(input);
        if (!input.equals(root)) {
            cli.confirm(MessageType.INFO,
                    "The path has been adjusted to " + Util.quote(root) + ".");
        }

        _anchorRoot = root;
    }

    private void getDeviceName(CLI cli) throws Exception
    {
        _deviceName = cli.askText(S.SETUP_DEV_ALIAS, _deviceName);
    }

    private void getS3Config(CLI cli) throws ExNoConsole
    {
        String s3BucketId = null;
        String s3AccessKey = null;
        String s3SecretKey = null;
        char[] s3EncryptionPassword = null;

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

        String scrypted = Base64.encodeBytes(SecUtil.scrypt(s3EncryptionPassword, _userID));

        _s3config = PBS3Config.newBuilder()
                .setBucketId(s3BucketId)
                .setAccessKey(s3AccessKey)
                .setSecretKey(s3SecretKey)
                .setEncryptionKey(scrypted)
                .build();
    }

    private char[] inputAndConfirmPasswd(CLI cli, String prompt) throws ExNoConsole
    {
        PasswordVerifier passwordVerifier = new PasswordVerifier();
        PasswordVerifierResult result;
        while (true) {
            char[] passwd = cli.askPasswd(prompt);
            result = passwordVerifier.verifyPassword(passwd);
            if (result == PasswordVerifierResult.OK) {
                char[] passwd2 = cli.askPasswd("Retype password");
                result = passwordVerifier.confirmPasswords(passwd, passwd2);
            }
            if (result != PasswordVerifierResult.OK) {
                cli.show(MessageType.ERROR, result.getMsg());
            } else {
                return passwd;
            }
        }
    }
}

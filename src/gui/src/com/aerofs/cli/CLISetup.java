package com.aerofs.cli;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.controller.InstallActor;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SetupModel.S3Options;
import com.aerofs.controller.SignInActor;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.S3DataEncryptionPasswordVerifier;
import com.aerofs.ui.S3DataEncryptionPasswordVerifier.PasswordVerifierResult;
import com.aerofs.ui.UIGlobals;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

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
            PROP_STORAGE_TYPE = "storage_type";

    private boolean _isUnattendedSetup;

    private String _anchorRoot = null;
    private StorageType _storageType = null;
    private SetupModel _model = null;

    CLISetup(CLI cli, String rtRoot) throws Exception
    {
        GetSetupSettingsReply defaults = UIGlobals.controller().getSetupSettings();

        _model = new SetupModel()
                .setSignInActor(LibParam.OpenId.ENABLED ?
                        new SignInActor.CLIOpenId(cli) : new SignInActor.Credential());

        _anchorRoot = defaults.getRootAnchor();
        _model.setDeviceName(defaults.getDeviceName());

        processSetupFile(rtRoot, _model._s3Options);

        if (!OSUtil.isLinux() && !_isUnattendedSetup) {
            cli.confirm(MessageType.WARN, L.product() + " CLI is not officially supported" +
                    " on non-Linux platforms. Specifically, GUI will be started" +
                    " up on the next automatic update.");
        }

        cli.show(MessageType.INFO, "Welcome to " + L.product() + ".");

        if (L.isMultiuser()) {
            _model.setInstallActor(new InstallActor.MultiUser());
            setupMultiuser(cli);
        } else {
            _model.setInstallActor(new InstallActor.SingleUser());
            setupSingleuser(cli);
        }

        if (OpenId.ENABLED) {
            _model.doSignIn();
            cli.progress(S.SETUP_INSTALL_MESSAGE);
            _model.doInstall();
        } else {
            cli.progress(S.SETUP_INSTALL_MESSAGE);
            _model.doSignIn();
            _model.doInstall();
        }

        cli.notify(MessageType.INFO,
                "---------------------------------------------------------------\n" +
                "You can now access " + L.product() + " functions through the\n" +
                Util.quote(S.SH_NAME) + " command while " +
                S.CLI_NAME + " is running.");
    }

    private void processSetupFile(String rtRoot, S3Options s3Options) throws Exception
    {
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

        _model.setUserID(props.getProperty(PROP_USERID));
        _model.setPassword(props.getProperty(PROP_PASSWORD));
        _model.setDeviceName(props.getProperty(PROP_DEVICE, _model.getDeviceName()));

        _anchorRoot = props.getProperty(PROP_ROOT, _anchorRoot);
        _storageType = StorageType.fromString(props.getProperty(PROP_STORAGE_TYPE));

        String s3BucketId = props.getProperty(CfgDatabase.Key.S3_BUCKET_ID.keyString());
        if (s3BucketId != null) {
            s3Options._bucketID = s3BucketId;
            s3Options._accessKey = props.getProperty(CfgDatabase.Key.S3_ACCESS_KEY.keyString());
            s3Options._secretKey = props.getProperty(CfgDatabase.Key.S3_SECRET_KEY.keyString());
            s3Options._passphrase = props.getProperty(CfgDatabase.Key.S3_ENCRYPTION_PASSWORD.keyString());

            if (_storageType == null) _storageType = StorageType.S3;
        }

        _isUnattendedSetup = true;
    }

    private void setupMultiuser(CLI cli) throws Exception
    {
        if (!_isUnattendedSetup) {
            getUser(cli);
            getPassword(cli);
            getDeviceName(cli);
            getStorageType(cli);
            if (_storageType == StorageType.S3) {
                getS3Config(cli, _model._s3Options);
            } else {
                getRootAnchor(cli);
            }
        }

        if (_storageType == StorageType.S3) {
            _model._isLocal = false;
        } else {
            _model._isLocal = true;
            _model._localOptions._rootAnchorPath = _anchorRoot;
            _model._localOptions._useBlockStorage = _storageType == StorageType.LOCAL;
        }
    }

    private void setupSingleuser(CLI cli) throws Exception
    {
        if (!_isUnattendedSetup) {
            getUser(cli);
            getPassword(cli);
            getDeviceName(cli);
            getRootAnchor(cli);
        }

        if (_storageType == null) _storageType = StorageType.LINKED;

        _model._isLocal = true;
        _model._localOptions._rootAnchorPath = _anchorRoot;
        _model._localOptions._useBlockStorage = false;
    }

    private void getUser(CLI cli)
            throws ExNoConsole, ExEmptyEmailAddress
    {
        if (LibParam.OpenId.ENABLED == false) {
            _model.setUserID(cli.askText(L.isMultiuser() ? S.ADMIN_EMAIL : S.SETUP_USER_ID, null));
        }
    }

    private void getPassword(CLI cli) throws Exception
    {
        if (LibParam.OpenId.ENABLED == false) {
            cli.show(MessageType.INFO, "If you forgot your password, go to " +
                    WWW.PASSWORD_RESET_REQUEST_URL + " to reset it.");
            _model.setPassword(String.valueOf(
                    cli.askPasswd(L.isMultiuser() ? S.ADMIN_PASSWD : S.SETUP_PASSWD)));
        }
    }

    private void getStorageType(CLI cli) throws Exception
    {
        StringBuilder bd = new StringBuilder();
        bd.append("The following storage options are available:");
        for (StorageType t : StorageType.values()) {
            bd.append("\n ").append(t.ordinal()).append(". ").append(t.description());
        }
        cli.show(MessageType.INFO, bd.toString());
        String s = cli.askText("Storage option", String.valueOf(StorageType.LINKED.ordinal()));
        try {
            _storageType = StorageType.fromOrdinal(Integer.valueOf(s));
        } catch (NumberFormatException e) {
            cli.show(MessageType.WARN, "Invalid option, using default");
            _storageType = StorageType.LINKED;
        } catch (IndexOutOfBoundsException e) {
            cli.show(MessageType.WARN, "Invalid option, using default");
            _storageType = StorageType.LINKED;
        }
    }

    private void getRootAnchor(CLI cli) throws Exception
    {
        String input = cli.askText(S.ROOT_ANCHOR, _anchorRoot);
        String root = RootAnchorUtil.adjustRootAnchor(input, null);
        if (!input.equals(root)) {
            cli.confirm(MessageType.INFO,
                    "The path has been adjusted to " + Util.quote(root) + ".");
        }

        _anchorRoot = root;
    }

    private void getDeviceName(CLI cli) throws Exception
    {
        _model.setDeviceName(cli.askText(S.SETUP_DEV_ALIAS, _model.getDeviceName()));
    }

    private void getS3Config(CLI cli, S3Options options) throws ExNoConsole
    {
        while (options._bucketID == null || options._bucketID.isEmpty()) {
            options._bucketID = cli.askText(S.SETUP_S3_BUCKET_NAME, null);
        }
        while (options._accessKey == null || options._accessKey.isEmpty()) {
            options._accessKey = cli.askText(S.SETUP_S3_ACCESS_KEY, null);
        }
        while (options._secretKey == null || options._secretKey.isEmpty()) {
            options._secretKey = cli.askText(S.SETUP_S3_SECRET_KEY, null);
        }
        while (options._passphrase == null || options._passphrase.length() == 0) {
            options._passphrase = String.valueOf(inputAndConfirmPasswd(cli,
                    S.SETUP_S3_ENCRYPTION_PASSWORD));
        }
    }

    private char[] inputAndConfirmPasswd(CLI cli, String prompt) throws ExNoConsole
    {
        S3DataEncryptionPasswordVerifier passwordVerifier = new S3DataEncryptionPasswordVerifier();
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

package com.aerofs.cli;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.base.ex.ExRateLimitExceeded;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.controller.InstallActor;
import com.aerofs.controller.Setup;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SetupModel.BackendConfig;
import com.aerofs.controller.SignInActor.CredentialActor;
import com.aerofs.controller.SignInActor.OpenIdCLIActor;
import com.aerofs.controller.UnattendedSetup;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.StorageDataEncryptionPasswordVerifier;
import com.aerofs.ui.StorageDataEncryptionPasswordVerifier.PasswordVerifierResult;

public class CLISetup
{
    private boolean _isUnattendedSetup;

    private SetupModel _model = null;

    CLISetup(CLI cli, String rtRoot) throws Exception
    {
        String defaultRootAnchor = Setup.getDefaultAnchorRoot();
        String defaultDeviceName = Setup.getDefaultDeviceName();

        _model = new SetupModel(rtRoot)
                .setSignInActor(LibParam.OpenId.enabled() ?
                        new OpenIdCLIActor(cli) : new CredentialActor());

        _model._localOptions._rootAnchorPath = defaultRootAnchor;
        _model.setDeviceName(defaultDeviceName);

        UnattendedSetup unattendedSetup = new UnattendedSetup(rtRoot);
        _isUnattendedSetup = unattendedSetup.setupFileExists();

        if (_isUnattendedSetup) {
            unattendedSetup.populateModelFromSetupFile(_model);
        }

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

        cli.progress(S.SETUP_SIGNIN_MESSAGE);
        _model.doSignIn();

        if (_model.getNeedSecondFactorSetup()) {
            throw new ExUIMessage(
                    "Your administrator requires that you enable two-factor authentication on" +
                            " your account before setting up new devices.\n" +
                    "Please set up two-factor authentication at:\n" +
                            "\n" + WWW.TWO_FACTOR_SETUP_URL + "\n" +
                            "\nThen, try setting up again."
            );
        }

        if (_model.getNeedSecondFactor()) {
            cli.show(MessageType.INFO,
                    "Two-Factor Authentication required for " + _model.getUsername());
            while (true) {
                String authCode = cli.askText("6-digit authentication code", null);
                Integer authCodeValue = null;
                try {
                    authCodeValue = Integer.parseInt(authCode, 10);
                } catch (NumberFormatException e) {
                    // invalid number
                    cli.show(MessageType.ERROR,
                            "That wasn't a valid 6-digit code.  Try again.");
                    continue;
                }
                _model.setSecondFactorCode(authCodeValue);
                try {
                    _model.doSecondFactorSignIn();
                } catch (ExSecondFactorRequired e) {
                    cli.show(MessageType.ERROR, "Incorrect authentication code.  Try again.");
                    continue;
                } catch (ExRateLimitExceeded e) {
                    cli.show(MessageType.ERROR,
                            "Rate limit exceeded for " + _model.getUsername() + ".\n" +
                            "Wait a minute, then try again.");
                    Thread.sleep(1 * C.MIN);
                    continue;
                }
                break;
            }
        }

        cli.progress(S.SETUP_REGISTERING_MESSAGE);
        _model.doInstall();

        cli.notify(MessageType.INFO,
                "You can now access " + L.product() + " functions through the " +
                Util.quote(S.SH_NAME) + " command while " + S.CLI_NAME + " is running.");
    }

    private void setupMultiuser(CLI cli) throws Exception
    {
        if (!_isUnattendedSetup) {
            getUser(cli);
            getPassword(cli);
            getDeviceName(cli);
            getStorageType(cli);
            if (_model._backendConfig._storageType == StorageType.S3) {
                getS3Config(cli, _model._backendConfig);
            } else if (_model._backendConfig._storageType == StorageType.SWIFT) {
                getSwiftConfig(cli, _model._backendConfig);
            } else {
                getRootAnchor(cli);
            }
            getAPIAccess(cli);
        }

        if (_model._backendConfig._storageType.isRemote()) {
            _model._isLocal = false;
        } else {
            _model._isLocal = true;
            _model._localOptions._useBlockStorage = _model._backendConfig._storageType == StorageType.LOCAL;
        }
    }

    private void setupSingleuser(CLI cli) throws Exception
    {
        if (!_isUnattendedSetup) {
            getUser(cli);
            getPassword(cli);
            getDeviceName(cli);
            getRootAnchor(cli);
            getAPIAccess(cli);
        }

        if (_model._backendConfig._storageType == null) _model._backendConfig._storageType = StorageType.LINKED;

        _model._isLocal = true;
        _model._localOptions._useBlockStorage = false;
    }

    private void getUser(CLI cli)
            throws ExNoConsole
    {
        if (!LibParam.OpenId.enabled()) {
            _model.setUserID(cli.askText(L.isMultiuser() ? S.ADMIN_EMAIL : S.SETUP_USER_ID, null));
        }
    }

    private void getPassword(CLI cli) throws Exception
    {
        if (LibParam.OpenId.enabled() == false) {
            cli.show(MessageType.INFO, "If you forgot your password, go to\n" +
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
            _model._backendConfig._storageType = StorageType.fromOrdinal(Integer.valueOf(s));
        } catch (NumberFormatException e) {
            cli.show(MessageType.WARN, "Invalid option, using default");
            _model._backendConfig._storageType = StorageType.LINKED;
        } catch (IndexOutOfBoundsException e) {
            cli.show(MessageType.WARN, "Invalid option, using default");
            _model._backendConfig._storageType = StorageType.LINKED;
        }
    }

    private void getRootAnchor(CLI cli) throws Exception
    {
        String input = cli.askText(S.ROOT_ANCHOR, _model._localOptions._rootAnchorPath);
        String root = RootAnchorUtil.adjustRootAnchor(input, null);
        if (!input.equals(root)) {
            cli.confirm(MessageType.INFO,
                    "The path has been adjusted to " + Util.quote(root) + ".");
        }

        _model._localOptions._rootAnchorPath = root;
    }

    private void getDeviceName(CLI cli) throws Exception
    {
        _model.setDeviceName(cli.askText(S.SETUP_DEV_ALIAS, _model.getDeviceName()));
    }

    private void getS3Config(CLI cli, BackendConfig cfg) throws ExNoConsole
    {
        while (cfg._s3Config._endpoint == null || cfg._s3Config._endpoint.isEmpty()) {
            cfg._s3Config._endpoint = cli.askText(S.SETUP_S3_ENDPOINT, LibParam.DEFAULT_S3_ENDPOINT);
        }
        while (cfg._s3Config._bucketID == null || cfg._s3Config._bucketID.isEmpty()) {
            cfg._s3Config._bucketID = cli.askText(S.SETUP_S3_BUCKET_NAME, null);
        }
        while (cfg._s3Config._accessKey == null || cfg._s3Config._accessKey.isEmpty()) {
            cfg._s3Config._accessKey = cli.askText(S.SETUP_S3_ACCESS_KEY, null);
        }
        while (cfg._s3Config._secretKey == null || cfg._s3Config._secretKey.isEmpty()) {
            cfg._s3Config._secretKey = cli.askText(S.SETUP_S3_SECRET_KEY, null);
        }
        while (cfg._passphrase == null || cfg._passphrase.length() == 0) {
            cfg._passphrase = String.valueOf(inputAndConfirmPasswd(cli,
                    S.SETUP_STORAGE_ENCRYPTION_PASSWORD));
        }
    }

    private void getSwiftConfig(CLI cli, BackendConfig cfg) throws ExNoConsole
    {
        while (cfg._swiftConfig._authMode == null || cfg._swiftConfig._authMode.isEmpty()
                || !("basic".equals(cfg._swiftConfig._authMode) || "keystone".equals(cfg._swiftConfig._authMode))) {
            cfg._swiftConfig._authMode = cli.askText(S.SETUP_SWIFT_AUTH_MODE_CLI, null);
        }
        while (cfg._swiftConfig._url == null || cfg._swiftConfig._url.isEmpty()) {
            cfg._swiftConfig._url = cli.askText(S.SETUP_SWIFT_URL, null);
        }
        while (cfg._swiftConfig._username == null || cfg._swiftConfig._username.isEmpty()) {
            cfg._swiftConfig._username = cli.askText(S.SETUP_SWIFT_USERNAME, null);
        }
        while (cfg._swiftConfig._password == null || cfg._swiftConfig._password.isEmpty()) {
            cfg._swiftConfig._password = cli.askText(S.SETUP_SWIFT_PASSWORD, null);
        }
        while (cfg._swiftConfig._container == null || cfg._swiftConfig._container.isEmpty()) {
            cfg._swiftConfig._container = cli.askText(S.SETUP_SWIFT_CONTAINER, null);
        }
        if ("keystone".equals(cfg._swiftConfig._authMode)) {
            cfg._swiftConfig._tenantId = cli.askText(S.SETUP_SWIFT_TENANT_ID, null);
            cfg._swiftConfig._tenantName = cli.askText(S.SETUP_SWIFT_TENANT_NAME, null);
        }
        while (cfg._passphrase == null || cfg._passphrase.length() == 0) {
            cfg._passphrase = String.valueOf(inputAndConfirmPasswd(cli,
                    S.SETUP_STORAGE_ENCRYPTION_PASSWORD));
        }
    }

    private void getAPIAccess(CLI cli) throws Exception
    {
        boolean enable = cli.ask(MessageType.INFO, "Enable " + S.MOBILE_AND_WEB_ACCESS + "? See "
                + S.URL_API_ACCESS + " for more information.");
        _model.enableAPIAccess(enable);
        cli.notify(MessageType.INFO, "API access is " + (enable ? "enabled" : "disabled") + ".");
    }

    private char[] inputAndConfirmPasswd(CLI cli, String prompt) throws ExNoConsole
    {
        StorageDataEncryptionPasswordVerifier passwordVerifier = new StorageDataEncryptionPasswordVerifier();
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

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgRestService;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.base.Preconditions;

/**
 * This class collects various pieces of information necessary to run setup and delegate to other
 * classes to perform the actual setup tasks. The implementation detail has evolved sufficiently
 * complicated to support various install and sign in methods.
 *
 * Here be dragons.
 *
 * FIXME(AT):
 * This class is originally intended to be used only with the multiuser setup dialog. Hence the
 * data it keeps is structured to work well with the GUI flow of the multiuser setup dialog.
 *
 * Over time, it has involved into the front-end wrapper for all setup & install flows. The class
 * has never been repurposed and its data structure has never been refactored. That's how we end
 * up with the mess in GUI unattended setup, CLISetup.setupSingleUser() and
 * CLISetup.setupMultiUser().
 */
public class SetupModel
{
    public SetupModel(String rtroot)
    {
        _setup = new Setup(rtroot);

        _devAlias = Setup.getDefaultDeviceName();
        _isLocal = true;
        // TODO: storage options should use inheritance
        _localOptions = new LocalOptions();
        _s3Config = new S3Config();
        _sp = null;
        // The default value is controlled by CfgRestService and provided here. For both clients
        // and Team Servers, this value should be used to initialize the UI state.
        _apiAccess = new CfgRestService().getDefaultValue();
    }

    /**
     * Authenticate the user and perform sign-in with SP. The result of this
     * is a signed-in SP client. The SP session is stored in the SetupModel instance.
     */
    public void doSignIn() throws Exception
    {
        Preconditions.checkNotNull(_signInActor, "SetupModel sign-in state error");
        _signInActor.signInUser(this);
        verifyConnected();
    }

    public void doSecondFactorSignIn() throws Exception
    {
        Preconditions.checkNotNull(_signInActor, "SetupModel sign-in state error");
        Preconditions.checkNotNull(_sp, "SP connection must be nonnull");
        _signInActor.provideSecondFactor(this);
        verifyConnected();
    }

    /**
     * Perform install steps as decided by the install actor.
     * Requires a signed-in SP connection.
     */
    public void doInstall() throws Exception
    {
        Preconditions.checkNotNull(_installActor, "SetupModel installation state error");
        verifyConnected();
        _installActor.install(_setup, this);
    }

    public SetupModel setSignInActor(SignInActor actor)
    {
        _signInActor = actor;
        return this;
    }

    public SetupModel setInstallActor(InstallActor actor)
    {
        _installActor = actor;
        return this;
    }

    public SPBlockingClient getClient()     { return _sp; }
    public void setClient(SPBlockingClient sp) { _sp = sp; }

    public String getDeviceName()           { return _devAlias; }
    public void setDeviceName(String name)  { _devAlias = name; }

    public byte[] getScrypted() throws ExInvalidID
                                            { return SecUtil.scrypt(
                                                getPasswordValue().toCharArray(),
                                                getUserID());
                                            }
    public byte[] getPassword()             { return getPasswordValue().getBytes(); }

    public void setPassword(String pw)      { _password = pw; }

    public boolean getNeedSecondFactor()    { return _needSecondFactor; }
    public void setNeedSecondFactor(boolean needed)
                                            { _needSecondFactor = needed; }

    public boolean getNeedSecondFactorSetup() { return _needSecondFactorSetup; }
    public void setNeedSecondFactorSetup(boolean needed)
                                            { _needSecondFactorSetup = needed; }

    public String getUsername()             { return _username; }
    public UserID getUserID() throws ExInvalidID
                                            { return UserID.fromExternal(_username); }
    public void setUserID(String username)  { _username = username; }

    public int getSecondFactorCode()        { return _secondFactorCode; }
    public void setSecondFactorCode(int code) { _secondFactorCode = code; }

    public boolean isAPIAccessEnabled()     { return _apiAccess; }
    public void enableAPIAccess(boolean enabled) { _apiAccess = enabled; }

    public void verifyConnected()           { Preconditions.checkNotNull(_sp, "Not signed in"); }

    // FIXME: the LocalOptions/S3Options classes are useless without polymorphism...
    // might as well just store all these directly in SetupModel.

    // handles the setup for using local storage
    public class LocalOptions
    {
        public String _rootAnchorPath;

        public boolean _useBlockStorage;

        public LocalOptions()
        {
            _rootAnchorPath = Setup.getDefaultAnchorRoot();
        }
        public String getDefaultRootAnchorPath()
        {
            return Setup.getDefaultAnchorRoot();
        }
    }

    // handles the setup for using S3 storage
    public static class S3Config
    {
        public String _endpoint;
        public String _bucketID;
        public String _accessKey;
        public String _secretKey;
        public String _passphrase;
    }

    private String getPasswordValue() { return (_password == null) ? "" : _password; }

    private final Setup     _setup;

    private SignInActor     _signInActor;
    private InstallActor    _installActor;

    private String          _username;
    private String          _password;
    private boolean         _needSecondFactor;
    private boolean         _needSecondFactorSetup;
    private int             _secondFactorCode;

    private boolean         _apiAccess;

    private String          _devAlias;
    public boolean          _isLocal;
    public LocalOptions     _localOptions;
    public S3Config         _s3Config;

    private SPBlockingClient _sp;

    public StorageType      _storageType;
}

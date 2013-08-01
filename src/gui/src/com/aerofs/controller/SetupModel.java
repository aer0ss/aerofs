/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.base.Base64;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.SecUtil;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.aerofs.sp.client.SPBlockingClient;

/**
 * This class acts as the model and supports the operations done on
 *   com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.
 */
public class SetupModel
{
    public SetupModel()
    {
        _setup = ControllerService.get().getSetup();

        _devAlias = _setup.getDefaultDeviceName();
        _isLocal = true;
        // TODO: storage options should use inheritance
        _localOptions = new LocalOptions();
        _s3Options = new S3Options();
        _sp = null;
    }

    /**
     * Authenticate the user and perform sign-in with SP. The result of this
     * is a signed-in SP client. The SP session is stored in the SetupModel instance.
     */
    public void doSignIn() throws Exception
    {
        assert _signInActor != null : "SetupModel sign-in state error";
        _signInActor.signInUser(_setup, this);
        assertConnected();
    }

    /**
     * Perform install steps as decided by the install actor.
     * Requires a signed-in SP connection.
     */
    public void doInstall() throws Exception
    {
        assert _installActor != null : "SetupModel installation state error";
        assertConnected();
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

    public byte[] getScrypted() throws ExEmptyEmailAddress
                                            { return SecUtil.scrypt(getPassword(), getUserID()); }
    public char[] getPassword()             { return (_password == null)
                                                ? new char[0] : _password.toCharArray(); }

    public void setPassword(String pw)      { _password = pw; }

    public String getUsername()             { return _username; }
    public UserID getUserID() throws ExEmptyEmailAddress
                                            { return UserID.fromExternal(_username); }
    public void setUserID(String username)  { _username = username; }

    public void assertConnected()           { assert _sp != null: "Not signed in"; }

    // FIXME: the LocalOptions/S3Options classes are useless without polymorphism...
    // might as well just store all these directly in SetupModel.

    // handles the setup for using local storage
    public class LocalOptions
    {
        public String _rootAnchorPath;

        public boolean _useBlockStorage;

        public LocalOptions()
        {
            _rootAnchorPath = _setup.getDefaultAnchorRoot();
        }
        public String getDefaultRootAnchorPath()
        {
            return _setup.getDefaultAnchorRoot();
        }
    }

    // handles the setup for using S3 storage
    public class S3Options
    {
        public String _bucketID;
        public String _accessKey;
        public String _secretKey;
        public String _passphrase;
        PBS3Config getConfig() throws ExEmptyEmailAddress
        {
            String scrypted = Base64.encodeBytes(
                    SecUtil.scrypt(_passphrase.toCharArray(), getUserID()));
            return PBS3Config.newBuilder()
                    .setBucketId(_bucketID)
                    .setAccessKey(_accessKey)
                    .setSecretKey(_secretKey)
                    .setEncryptionKey(scrypted)
                    .build();
        }
    }

    private final Setup     _setup;

    private SignInActor     _signInActor;
    private InstallActor    _installActor;

    private String          _username;
    private String          _password;

    private String          _devAlias;
    public boolean          _isLocal;
    public LocalOptions     _localOptions;
    public S3Options        _s3Options;

    private SPBlockingClient _sp;
}
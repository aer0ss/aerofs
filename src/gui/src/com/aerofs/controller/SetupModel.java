/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.base.Base64;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.proto.ControllerProto.PBS3Config;

/**
 * This class acts as the model and supports the operations done on
 *   com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.
 */
public class SetupModel
{
    // the Setup instance handles all actual operations behind the scene
    private Setup _setup;

    /**
     * cache user input values so we can read them when we execute
     *   operations on a separate thread
     */
    public String _username;
    public String _password;
    public String _devAlias;

    public boolean _isLocal;

    public LocalOptions _localOptions;
    public S3Options _s3Options;

    /**
     * since we split up sign in and install operations, it's useful
     *   to cache these values so we don't have to recompute them.
     */
    private UserID _userID;
    private char[] _passwd;

    // populate values with default values
    public SetupModel()
    {
        _setup = ControllerService.get().getSetup();

        _devAlias = _setup.getDefaultDeviceName();
        _isLocal = true;
        _localOptions = new LocalOptions();
        _s3Options = new S3Options();
    }

    // this method is called by the GUI on a separate thread to perform
    //   the sign in operation
    // TODO (AT): investigate and refactor setup process, see Setup.signInUser()
    public void signIn()
            throws Exception
    {
        _userID = UserID.fromExternal(_username);
        _passwd = _password.toCharArray();
        _setup.signInUser(_userID, _passwd);
    }

    // this method is called by the GUI on a separate thread to perform
    //   the install operation
    public void install()
            throws Exception
    {
        if (_isLocal) _localOptions.install();
        else _s3Options.install();
    }

    // handles the setup for using local storage
    public class LocalOptions
    {
        public String _rootAnchorPath;
        public boolean _useBlockStorage;

        public LocalOptions()
        {
            _rootAnchorPath = _setup.getDefaultAnchorRoot();
        }

        protected void install()
                throws Exception
        {
            _setup.setupMultiuser(_userID, _passwd, _rootAnchorPath, _devAlias,
                    _useBlockStorage ? StorageType.LOCAL : StorageType.LINKED, null);
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

        private PBS3Config getConfig()
        {
            String scrypted = Base64.encodeBytes(
                    SecUtil.scrypt(_passphrase.toCharArray(), _userID));
            return PBS3Config.newBuilder()
                    .setBucketId(_bucketID)
                    .setAccessKey(_accessKey)
                    .setSecretKey(_secretKey)
                    .setEncryptionKey(scrypted)
                    .build();
        }

        protected void install()
                throws Exception
        {
            _setup.setupMultiuser(_userID, _passwd, _setup.getDefaultAnchorRoot(),
                    _devAlias, StorageType.S3, getConfig());
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.multiuser.MultiuserDlgSetupAdvanced.S3Config;
import com.aerofs.gui.setup.AbstractDlgSetup;
import com.aerofs.gui.setup.AbstractDlgSetupAdvanced;
import com.aerofs.labeling.L;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import org.eclipse.swt.widgets.Shell;

public class MultiuserDlgSetup extends AbstractDlgSetup
{
    private S3Config _s3Config;
    private MultiuserDlgSetupAdvanced _advanced;
    private StorageType _storageChoice = StorageType.LOCAL;

    public MultiuserDlgSetup(Shell parentShell)
            throws Exception
    {
        super(parentShell);
    }

    @Override
    public void setup(String userID, char[] passwd)
            throws Exception
    {
        PBS3Config config = getS3Config(UserID.fromExternal(userID));
        UI.controller().setupMultiuser(userID, new String(passwd), getAbsRootAnchor(),
                getDeviceName(), _storageChoice.name(), config);
    }

    @Override
    public void postSetup()
    {
        if (GUI.get().ask(MessageType.QUESTION,
                "Do you want to invite users to your team now?" +
                " You can also do it later on the " + L.product() + " web site.",
                "Invite Users", "Later")) {
            GUIUtil.launch(WWW.TEAM_MEMBERS_URL.get());
        }
    }

    protected AbstractDlgSetupAdvanced createAdvancedSetupDialog()
    {
        _advanced = new MultiuserDlgSetupAdvanced(getShell(), getDeviceName(), getAbsRootAnchor(),
                _s3Config, _storageChoice);
        return _advanced;
    }

    private PBS3Config getS3Config(UserID userID)
    {
        if (_storageChoice == StorageType.S3) {
            String scrypted = Base64.encodeBytes(
                    SecUtil.scrypt(_s3Config.s3Passphrase.toCharArray(), userID));
            return PBS3Config.newBuilder()
                    .setBucketId(_s3Config.s3BucketId)
                    .setAccessKey(_s3Config.s3AccessKey)
                    .setSecretKey(_s3Config.s3SecretKey)
                    .setEncryptionKey(scrypted)
                    .build();
        } else {
            return null;
        }
    }

    @Override protected void processAdvancedSettings()
    {
        _storageChoice = _advanced.getStorageChoice();
        _s3Config = _advanced.getS3Config();
    }
}

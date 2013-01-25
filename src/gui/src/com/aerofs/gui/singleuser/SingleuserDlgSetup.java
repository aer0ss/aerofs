/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.singleuser;

import java.io.IOException;

import com.aerofs.gui.Images;
import com.aerofs.gui.setup.AbstractDlgSetup;
import com.aerofs.gui.setup.AbstractDlgSetupAdvanced;
import com.aerofs.labeling.L;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

public class SingleuserDlgSetup extends AbstractDlgSetup
{
    public SingleuserDlgSetup(Shell parentShell)
            throws Exception
    {
        super(parentShell);

        setTitleImage(Images.get(Images.IMG_SETUP));
    }

    @Override
    public void setup(String userID, char[] passwd)
            throws Exception
    {
        UI.controller().setupSingleuser(userID, new String(passwd), getAbsRootAnchor(),
                getDeviceName(), null);

        // setup shell extension
        while (true) {
            try {
                OSUtil.get().installShellExtension(false);
                break;
            } catch (SecurityException e) {
                if (!UI.get()
                        .ask(MessageType.QUESTION,
                                L.PRODUCT + " needs your authorization to install the " +
                                        OSUtil.get().getShellExtensionName() + ".\n\n" +
                                        "Would you like to retry entering your password?\n" +
                                        "If you click Cancel, the " +
                                        OSUtil.get().getShellExtensionName() +
                                        " won't be available.", IDialogConstants.OK_LABEL,
                                IDialogConstants.CANCEL_LABEL)) {
                    break;
                }
            } catch (IOException e) {
                l.warn("Installing shell extension failed: " + Util.e(e));
                break;
            }
        }
    }

    @Override
    public void postSetup()
    {
    }

    @Override
    protected AbstractDlgSetupAdvanced createAdvancedSetupDialog()
    {
        return new SingleuserDlgSetupAdvanced(getShell(), getDeviceName(), getAbsRootAnchor());
    }

    @Override
    protected void processAdvancedSettings()
    {
        // no-op
    }
}

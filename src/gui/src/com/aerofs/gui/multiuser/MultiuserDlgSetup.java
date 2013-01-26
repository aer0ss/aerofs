/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.setup.AbstractDlgSetup;
import com.aerofs.base.BaseParam.SP;
import com.aerofs.labeling.L;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import org.eclipse.swt.widgets.Shell;

public class MultiuserDlgSetup extends AbstractDlgSetup
{
    public MultiuserDlgSetup(Shell parentShell)
            throws Exception
    {
        super(parentShell);
    }

    @Override
    public void setup(String userID, char[] passwd)
            throws Exception
    {
        UI.controller().setupMultiuser(userID, new String(passwd), getAbsRootAnchor(),
                getDeviceName(), null);
    }

    @Override
    public void postSetup()
    {
        if (GUI.get().ask(MessageType.QUESTION,
                "Do you want to invite users to your team now?" +
                " You can also do it later on the " + L.PRODUCT + " web site.",
                "Invite Users", "Later")) {
            GUIUtil.launch(SP.TEAM_MANAGEMENT_LINK);
        }
    }
}

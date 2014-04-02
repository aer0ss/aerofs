
package com.aerofs.gui.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.S;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

// a helper class for setup dialogs relating to API access.
public class APIAccessSetupHelper
{
    public final boolean _showAPIAccess;

    public Button _chkAPIAccess;
    public Link _lnkAPIAccess;

    public APIAccessSetupHelper()
    {
        _showAPIAccess = !PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT;
    }

    public void createCheckbox(Composite parent)
    {
        final Shell shell = parent.getShell();

        _chkAPIAccess = GUIUtil.createButton(parent, SWT.CHECK);
        _chkAPIAccess.setText("Enable");
        _chkAPIAccess.setSelection(true);
        _chkAPIAccess.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!_chkAPIAccess.getSelection()) {
                    if (!GUI.get().ask(shell, MessageType.INFO, S.API_ACCESS_WARN)) {
                        _chkAPIAccess.setSelection(true);
                    }
                }
            }
        });
    }

    public void createLink(Composite parent)
    {
        _lnkAPIAccess = new Link(parent, SWT.NONE);
        _lnkAPIAccess.setText("<a>API access</a>");
        _lnkAPIAccess.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_API_ACCESS));
    }

    public GridData createLinkLayoutData(GridData data)
    {
        if (OSUtil.isOSX()) {
            data.verticalIndent = 2;
        } else if (OSUtil.isLinux()) {
            data.verticalIndent = 3;
        }

        return data;
    }

    public void readFromModel(SetupModel model)
    {
        if (_showAPIAccess) {
            _chkAPIAccess.setSelection(model.isAPIAccessEnabled());
        }
    }

    public void writeToModel(SetupModel model)
    {
        if (_showAPIAccess) {
            model.enableAPIAccess(_chkAPIAccess.getSelection());
        }
    }
}

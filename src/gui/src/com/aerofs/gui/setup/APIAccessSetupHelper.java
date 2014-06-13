
package com.aerofs.gui.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.S;
import com.aerofs.lib.os.OSUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;

/**
 * A helper class for setup dialogs relating to API access.
 *
 * The intention is for the setup dialogs and pages to call the helper methods to create the
 * widgets first, and then the dialogs and pages will provide the model to initialize the widgets'
 * states. The default state of the widget is entirely controlled by the model.
 */
public class APIAccessSetupHelper
{
    public final boolean _showAPIAccess;

    public Button _chkAPIAccess;
    public Link _lnkAPIAccess;

    public APIAccessSetupHelper()
    {
        _showAPIAccess = PrivateDeploymentConfig.isHybridDeployment();
    }

    public void createCheckbox(Composite parent)
    {
        _chkAPIAccess = GUIUtil.createButton(parent, SWT.CHECK);
        _chkAPIAccess.setText("Enable");
    }

    public void createLink(Composite parent)
    {
        _lnkAPIAccess = new Link(parent, SWT.NONE);
        _lnkAPIAccess.setText("<a>" + S.MOBILE_AND_WEB_ACCESS + "</a>");
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

    // this method _should always_ be called at least once to initialize the value for checkbox
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

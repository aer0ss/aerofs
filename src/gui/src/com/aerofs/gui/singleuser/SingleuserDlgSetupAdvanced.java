/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.singleuser;

import com.aerofs.gui.setup.AbstractDlgSetupAdvanced;
import com.aerofs.gui.setup.CompLocalStorage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

public class SingleuserDlgSetupAdvanced extends AbstractDlgSetupAdvanced
{
    private CompLocalStorage _compLocalStorage;

    public SingleuserDlgSetupAdvanced(Shell parentShell, String deviceName, String absRootAnchor)
    {
        super(parentShell, deviceName, absRootAnchor);
    }

    @Override
    protected void createStorageArea(Composite container)
    {
        _compLocalStorage = new CompLocalStorage(container, getAbsoluteRootAnchor());

        getShell().addListener(SWT.Show, new Listener() {

            @Override
            public void handleEvent(Event event)
            {
                _compLocalStorage.setAbsRootAnchor(getAbsoluteRootAnchor());
            }
        });
    }

    @Override
    protected String getAbsRootAnchorFromTxtField()
    {
        return _compLocalStorage.getAbsRootAnchor();
    }
}

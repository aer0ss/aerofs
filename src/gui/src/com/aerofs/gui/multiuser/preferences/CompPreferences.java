/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.preferences.PreferencesUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class CompPreferences extends Composite
{
    private final Text _txtRootAnchor;
    private final PreferencesUtil _preferencesUtil;

    public CompPreferences(Composite parent) {
        super(parent, SWT.NONE);

        _preferencesUtil = new PreferencesUtil(this);

        GridLayout gridLayout = new GridLayout(3, false);

        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 10;
        this.setLayout(gridLayout);

        // Root anchor location row

        Label lblAerofsLocation = new Label(this, SWT.NONE);
        lblAerofsLocation.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblAerofsLocation.setText("Storage location:");

        _txtRootAnchor = new Text(this, SWT.BORDER | SWT.READ_ONLY);
        GridData gd__txtRootAnchor = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd__txtRootAnchor.widthHint = 100;
        _txtRootAnchor.setLayoutData(gd__txtRootAnchor);
        _txtRootAnchor.setText(Cfg.absRootAnchor());

        Button btnMoveRoot = new Button(this, SWT.NONE);
        btnMoveRoot.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
        btnMoveRoot.setText("Move...");
        btnMoveRoot.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                selectAndMoveRootAnchor_();
            }
        });
    }

    private void selectAndMoveRootAnchor_()
    {
        // Have to re-open the directory dialog in a separate stack, since doing it in the same
        // stack would cause strange SWT crashes on OSX :/
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run()
            {
                String root = _preferencesUtil.getRootAnchorPathFromDirectoryDialog("Select storage location");
                if (root == null) return; //User hit cancel
                if (_preferencesUtil.moveRootAnchor(root)) {
                    _txtRootAnchor.setText(_preferencesUtil.getRootAnchor());
                } else {
                    selectAndMoveRootAnchor_();
                }
            }
        });
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.preferences.PreferencesHelper;
import com.aerofs.lib.S;
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

public class MultiuserCompPreferences extends Composite
{
    private final Text _txtRootAnchor;
    private final PreferencesHelper _helper;

    public MultiuserCompPreferences(Composite parent) {
        super(parent, SWT.NONE);

        _helper = new PreferencesHelper(this);

        GridLayout gridLayout = new GridLayout(3, false);

        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 10;
        this.setLayout(gridLayout);

        // Root anchor location row

        Label lblRootAnchor = new Label(this, SWT.NONE);
        lblRootAnchor.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblRootAnchor.setText(S.ROOT_ANCHOR);

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
                _helper.selectAndMoveRootAnchor(_txtRootAnchor);
            }
        });
    }
}

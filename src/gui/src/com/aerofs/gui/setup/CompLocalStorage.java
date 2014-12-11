/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.controller.Setup;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.google.common.collect.Lists;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import java.util.List;

import static com.aerofs.gui.GUIUtil.createLabel;

public class CompLocalStorage
{
    private final Composite _container;
    private String _absRootAnchor;
    private Text _txtRoot;

    private final List<Control> _controls = Lists.newArrayList();

    public CompLocalStorage(Composite container, String absRootAnchor)
    {
        _absRootAnchor = absRootAnchor;

        _container = container;

        addLabel();

        Composite _txtComposite = createCompositeForFields();

        addTextBox(_txtComposite);

        addChangeLocationButton(_txtComposite);

        addUserDefaultLocationButton(_txtComposite);
    }

    private void addUserDefaultLocationButton(Composite _txtComposite)
    {
        Button btnUserDefaultLocation = GUIUtil.createButton(_txtComposite, SWT.NONE);
        btnUserDefaultLocation.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    _absRootAnchor = Setup.getDefaultAnchorRoot();
                } catch (Exception e1) {
                    assert false;
                }
                _txtRoot.setText(_absRootAnchor);
            }
        });
        btnUserDefaultLocation.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
        btnUserDefaultLocation.setText("Use Default");
        _controls.add(btnUserDefaultLocation);
    }

    private void addChangeLocationButton(Composite _txtComposite)
    {
        Button btnChangeLocation = GUIUtil.createButton(_txtComposite, SWT.NONE);
        btnChangeLocation.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                DirectoryDialog dd = new DirectoryDialog(_container.getShell(), SWT.SHEET);
                dd.setMessage("Select " + S.ROOT_ANCHOR);
                String root = dd.open();
                if (root != null) {
                    _absRootAnchor = RootAnchorUtil.adjustRootAnchor(root, null);
                    _txtRoot.setText(_absRootAnchor);
                }
            }
        });
        btnChangeLocation.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
        btnChangeLocation.setText(S.BTN_CHANGE);
        _controls.add(btnChangeLocation);
    }

    private void addTextBox(Composite _txtComposite)
    {
        _txtRoot = new Text(_txtComposite, SWT.BORDER | SWT.READ_ONLY);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        gd.widthHint = 100;
        _txtRoot.setLayoutData(gd);
        _controls.add(_txtRoot);
    }

    private Composite createCompositeForFields()
    {
        Composite _txtComposite = new Composite(_container, SWT.NONE);
        GridLayout glTxtComposite = new GridLayout(2, false);
        glTxtComposite.marginHeight = 0;
        glTxtComposite.marginWidth = 0;
        _txtComposite.setLayout(glTxtComposite);
        _txtComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1));
        _controls.add(_txtComposite);
        return _txtComposite;
    }

    private void addLabel()
    {
        Label lbl = createLabel(_container, SWT.NONE);
        lbl.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lbl.setText(S.ROOT_ANCHOR + ":");
        _controls.add(lbl);
    }

    public void setVisible(boolean visible)
    {
        for (Control c : _controls) {
            c.setVisible(visible);
            ((GridData)c.getLayoutData()).exclude = !visible;
        }
    }

    public String getAbsRootAnchor()
    {
        return _absRootAnchor;
    }

    public void setAbsRootAnchor(String absRootAnchor)
    {
        _absRootAnchor = absRootAnchor;
        _txtRoot.setText(_absRootAnchor);
    }
}

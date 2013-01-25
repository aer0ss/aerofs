/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import javax.annotation.Nullable;

public class CompLocalStorage extends Composite
{
    private String _absRootAnchor;
    private Text _txtRoot;

    public CompLocalStorage(Composite container, String absRootAnchor)
    {
        this(container, absRootAnchor, null);
    }

    public CompLocalStorage(Composite container, String absRootAnchor, @Nullable String explanation)
    {
        super(container, SWT.NONE);
        _absRootAnchor = absRootAnchor;

        initializeLayout();

        if (explanation != null) {
            new Label(this, SWT.NONE);
            new Label(this, SWT.NONE);

            addExplanation(explanation);

            new Label(this, SWT.NONE);
            new Label(this, SWT.NONE);
        }

        addLabel();

        Composite _txtComposite = createCompositeForFields();

        addTextBox(_txtComposite);

        addChangeLocationButton(_txtComposite);

        addUserDefaultLocationButton(_txtComposite);
    }

    private void addExplanation(String explanation)
    {
        Label lbl = new Label(this, SWT.WRAP);
        lbl.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1));
        lbl.setText(explanation);
    }

    private void addUserDefaultLocationButton(Composite _txtComposite)
    {
        Button btnUserDefaultLocation = new Button(_txtComposite, SWT.NONE);
        btnUserDefaultLocation.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                try {
                    _absRootAnchor = UI.controller().getSetupSettings().getRootAnchor();
                } catch (Exception e1) {
                    assert false;
                }
                _txtRoot.setText(_absRootAnchor);
            }
        });
        btnUserDefaultLocation.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        btnUserDefaultLocation.setText("Use Default");
    }

    private void addChangeLocationButton(Composite _txtComposite)
    {
        Button btnChangeLocation = new Button(_txtComposite, SWT.NONE);
        btnChangeLocation.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                DirectoryDialog dd = new DirectoryDialog(getShell(), SWT.SHEET);
                dd.setMessage("Select " + S.ROOT_ANCHOR);
                String root = dd.open();
                if (root != null) {
                    _absRootAnchor = RootAnchorUtil.adjustRootAnchor(root);
                    _txtRoot.setText(_absRootAnchor);
                }
            }
        });
        btnChangeLocation.setText(S.BTN_CHANGE);
    }

    private void addTextBox(Composite _txtComposite)
    {
        _txtRoot = new Text(_txtComposite, SWT.BORDER | SWT.READ_ONLY);
        _txtRoot.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    }

    private Composite createCompositeForFields()
    {
        Composite _txtComposite = new Composite(this, SWT.NONE);
        GridLayout glTxtComposite = new GridLayout(2, false);
        glTxtComposite.marginHeight = 0;
        glTxtComposite.marginWidth = 0;
        _txtComposite.setLayout(glTxtComposite);
        _txtComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1));
        return _txtComposite;
    }

    private void addLabel()
    {
        Label lbl = new Label(this, SWT.NONE);
        lbl.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lbl.setText(S.ROOT_ANCHOR + ":");
    }

    private void initializeLayout()
    {
        GridLayout glComposite = new GridLayout(2, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight= 0;
        this.setLayout(glComposite);
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

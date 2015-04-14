/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.S;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import static com.aerofs.gui.GUIUtil.createLabel;

/**
 * This class serves as a base template for all pages used in the multiuser setup dialog.
 *
 * It outlines a general page layout defined as follows:
 *   page := { header, body }, body := { content, button bar }
 *
 * Individual layout parts can be extended by subclasses if needed.
 *
 * In addition, this class also wires the page with SetupModel.
 */
public abstract class AbstractSetupPage extends Composite
{
    protected DlgMultiuserSetup _dialog;
    protected SetupModel _model;

    protected AbstractSetupPage(Composite parent, int style)
    {
        super(parent, style);
    }

    public void setDialog(DlgMultiuserSetup dialog)
    {
        _dialog = dialog;
    }

    public void setModel(SetupModel model)
    {
        _model = model;
    }

    // subclass should override this to update the UI state based on the values in the model
    protected void readFromModel(SetupModel model)
    {

    }

    // subclass should override this to update the model based on the UI state
    protected void writeToModel(SetupModel model)
    {

    }

    public void initPage()
    {
        Composite header = createHeader(this);
        Composite body = createBody(this);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);

        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    protected Composite createHeader(Composite parent)
    {
        Composite header = new Composite(parent, SWT.NONE);
        header.setBackgroundMode(SWT.INHERIT_FORCE);
        header.setBackground(SWTResourceManager.getColor(0xFF, 0xFF, 0xFF));

        Label title = createLabel(header, SWT.NONE);
        title.setText(S.SETUP_TITLE);
        GUIUtil.updateFont(title, 120, SWT.BOLD);

        Label logo = createLabel(header, SWT.NONE);
        logo.setImage(Images.get(Images.IMG_SETUP));

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.marginLeft = 20;
        header.setLayout(layout);

        title.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, true));
        logo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, true));

        return header;
    }

    protected Composite createBody(Composite parent)
    {
        Composite body = new Composite(parent, SWT.NONE);

        Composite content = createContent(body);
        Composite buttonBar = createButtonBar(body);

        GridLayout layout = new GridLayout();
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.marginWidth = 40;
        layout.verticalSpacing = 10;
        body.setLayout(layout);

        content.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));

        return body;
    }

    protected abstract Composite createContent(Composite parent);

    protected Composite createButtonBar(Composite parent)
    {
        Composite buttonBar = new Composite(parent, SWT.NONE);

        populateButtonBar(buttonBar);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.center = true;
        buttonBar.setLayout(layout);

        return buttonBar;
    }

    protected abstract void populateButtonBar(Composite parent);

    protected static final int BUTTON_DEFAULT = 1;
    protected static final int BUTTON_BACK = 2;

    // an utility method to create a button for the button bar
    protected Button createButton(Composite parent, String text, int style)
    {
        Button button = GUIUtil.createButton(parent, SWT.NONE);

        button.setText(text);
        button.setLayoutData(new RowData(100, SWT.DEFAULT));

        if ((style & BUTTON_DEFAULT) != 0) {
            getShell().setDefaultButton(button);
        }

        if ((style & BUTTON_BACK) != 0) {
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    goPreviousPage();
                }
            });
        }

        return button;
    }

    protected abstract void goNextPage();
    protected abstract void goPreviousPage();
}

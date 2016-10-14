/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.javaswift.joss.model.Container;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.google.common.base.Objects.firstNonNull;

public class PageSwiftContainer extends AbstractSetupPage
{
    protected Combo     _comboContainer;

    protected Button    _btnNext;

    public PageSwiftContainer(Composite parent) {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Label lblMessage = createLabel(content, SWT.NONE);
        lblMessage.setText(S.SETUP_SWIFT_SELECT_CONTAINER);

        Composite compContainer = createContainerListComposite(content);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 10;
        content.setLayout(layout);

        lblMessage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return content;
    }

    @Override
    protected void goNextPage()
    {
        _dialog.loadPage(DlgMultiuserSetup.PageID.PAGE_PASSPHRASE);
    }

    @Override
    protected void goPreviousPage() {
        if ("basic".equals(_model._backendConfig._swiftConfig._authMode)) {
            _dialog.loadPage(DlgMultiuserSetup.PageID.PAGE_SWIFT_STORAGE);
        }
        else if ("keystone".equals(_model._backendConfig._swiftConfig._authMode)) {
            // TODO: Keystone Tenant Page
            _dialog.loadPage(DlgMultiuserSetup.PageID.PAGE_SWIFT_STORAGE);
        }
    }

    protected Composite createContainerListComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblContainer = createLabel(composite, SWT.NONE);
        lblContainer.setText(S.SETUP_SWIFT_CONTAINER_GUI);
        _comboContainer = new Combo(composite, SWT.BORDER | SWT.DROP_DOWN);
        _comboContainer.setFocus();

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 60;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        composite.setLayout(layout);

        lblContainer.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _comboContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return composite;
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        createButton(parent, S.BTN_BACK, BUTTON_BACK);

        _btnNext = createButton(parent, S.BTN_CONTINUE, BUTTON_DEFAULT);
        _btnNext.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                writeToModel(_model);
                goNextPage();
            }
        });
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        // Fills the container list
        _comboContainer.removeAll();
        for (Container container : _model._backendConfig._swiftConfig._containerList) {
            _comboContainer.add(container.getName());
        }

        _comboContainer.setText(firstNonNull(model._backendConfig._swiftConfig._container, ""));
        _comboContainer.setFocus();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._backendConfig._swiftConfig._container = _comboContainer.getText().trim();
    }
}

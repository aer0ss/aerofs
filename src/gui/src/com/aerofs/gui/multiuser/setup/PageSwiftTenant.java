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

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.google.common.base.Objects.firstNonNull;

public class PageSwiftTenant extends AbstractSetupPage
{
    protected Text      _txtTenantId;
    protected Text      _txtTenantName;

    protected Button    _btnNext;

    public PageSwiftTenant(Composite parent) {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Label lblMessage = createLabel(content, SWT.WRAP);
        lblMessage.setText(S.SETUP_SWIFT_SELECT_TENANT);

        Composite compTenant = createTenantComposite(content);

        Label lblExperimental = createLabel(content, SWT.WRAP);
        lblExperimental.setText(S.SETUP_SWIFT_KEYSTONE_BETA);
        lblExperimental.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 10;
        content.setLayout(layout);

        lblMessage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compTenant.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lblExperimental.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return content;
    }

    @Override
    protected void goNextPage()
    {
        _dialog.loadPage(DlgMultiuserSetup.PageID.PAGE_SWIFT_CONTAINER);
    }

    @Override
    protected void goPreviousPage() {
        _dialog.loadPage(DlgMultiuserSetup.PageID.PAGE_SWIFT_STORAGE);
    }

    protected Composite createTenantComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblTenantId = createLabel(composite, SWT.NONE);
        lblTenantId.setText(S.SETUP_SWIFT_TENANT_ID_GUI);
        _txtTenantId = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        Label lblTenantName = createLabel(composite, SWT.NONE);
        lblTenantName.setText(S.SETUP_SWIFT_TENANT_NAME_GUI);
        _txtTenantName = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 20;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        composite.setLayout(layout);

        lblTenantId.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtTenantId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblTenantName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtTenantName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

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
        _txtTenantId.setText(firstNonNull(model._backendConfig._swiftConfig._tenantId, ""));
        _txtTenantName.setText(firstNonNull(model._backendConfig._swiftConfig._tenantName, ""));
        _txtTenantId.setFocus();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._backendConfig._swiftConfig._tenantId = _txtTenantId.getText().trim();
        model._backendConfig._swiftConfig._tenantName = _txtTenantName.getText().trim();
    }
}

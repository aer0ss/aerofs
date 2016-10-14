/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.PageID;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import static com.aerofs.gui.GUIUtil.updateFont;
import static com.aerofs.gui.GUIUtil.createLabel;

public class PageSelectStorage extends AbstractSetupPage
{
    private Button      _btnLocalStorage;
    private Button      _btnS3Storage;
    private Button      _btnSwiftStorage;

    private Button      _btnNext;

    public PageSelectStorage(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Label lblMessage = createLabel(content, SWT.NONE);
        updateFont(lblMessage, 110, SWT.NONE);
        lblMessage.setText(S.SETUP_STORAGE_MESSAGE);

        createChoicesComposite(content);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.spacing = 15;
        layout.center = true;
        content.setLayout(layout);

        return content;
    }

    protected Composite createChoicesComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        _btnLocalStorage = GUIUtil.createButton(composite, SWT.RADIO);
        _btnLocalStorage.setText(S.SETUP_STORAGE_LOCAL);
        _btnLocalStorage.setFocus();

        _btnS3Storage = GUIUtil.createButton(composite, SWT.RADIO);
        _btnS3Storage.setText(S.SETUP_STORAGE_S3);

        _btnSwiftStorage = GUIUtil.createButton(composite, SWT.RADIO);
        _btnSwiftStorage.setText(S.SETUP_STORAGE_SWIFT);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.spacing = 10;
        composite.setLayout(layout);

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
    protected void goNextPage()
    {
        if (_model._isLocal) {
            _dialog.loadPage(PageID.PAGE_LOCAL_STORAGE);
        } else if (_model._backendConfig._storageType == StorageType.S3) {
            _dialog.loadPage(PageID.PAGE_S3_STORAGE);
        } else if (_model._backendConfig._storageType == StorageType.SWIFT) {
            _dialog.loadPage(PageID.PAGE_SWIFT_STORAGE);
        }
    }

    @Override
    protected void goPreviousPage()
    {
        _dialog.loadPage(PageID.PAGE_LOGIN);
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _btnLocalStorage.setSelection(model._isLocal);
        _btnS3Storage.setSelection(!model._isLocal && model._backendConfig._storageType == StorageType.S3);
        _btnSwiftStorage.setSelection(!model._isLocal && model._backendConfig._storageType == StorageType.SWIFT);

        if (model._isLocal) {
            _btnLocalStorage.setFocus();
        } else if (model._backendConfig._storageType == StorageType.S3) {
            _btnS3Storage.setFocus();
        } else if (model._backendConfig._storageType == StorageType.SWIFT) {
            _btnSwiftStorage.setFocus();
        } // else leave the focus to default
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._isLocal = _btnLocalStorage.getSelection();

        if (_btnS3Storage.getSelection()) {
            model._backendConfig._storageType = StorageType.S3;
        } else if (_btnSwiftStorage.getSelection()) {
            model._backendConfig._storageType = StorageType.SWIFT;
        }
    }
}

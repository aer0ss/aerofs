/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import static com.aerofs.gui.GUIUtil.createLabel;

public class PageSelectStorage extends AbstractSetupPage
{
    private Button      _btnLocalStorage;
    private Button      _btnS3Storage;

    public PageSelectStorage(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        createLabel(content, SWT.NONE).setText(S.SETUP_STORAGE_MESSAGE);

        createChoicesComposite(content);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.spacing = 30;
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

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.spacing = 30;
        composite.setLayout(layout);

        return composite;
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        Button btnBack = createButton(parent, S.BTN_BACK, false);
        btnBack.addSelectionListener(createListenerToGoBack());

        Button btnNext = createButton(parent, S.BTN_CONTINUE, true);
        btnNext.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                writeToModel(_model);
                traverse(SWT.TRAVERSE_PAGE_NEXT);
            }
        });
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _btnLocalStorage.setSelection(model._isLocal);
        _btnS3Storage.setSelection(!model._isLocal);
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._isLocal = _btnLocalStorage.getSelection();
    }
}

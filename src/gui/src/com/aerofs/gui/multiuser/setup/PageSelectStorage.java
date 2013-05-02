/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.S;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class PageSelectStorage extends AbstractSetupPage
{
    private Composite   _compHeader;
    private Composite   _compContent;
    private Composite   _compChoices;
    private Composite   _compButtons;

    private Label       _lblTitle;
    private Label       _lblLogo;
    private Label       _lblMessage;
    private Button      _btnLocalStorage;
    private Button      _btnS3Storage;
    private Button      _btnNext;
    private Button      _btnBack;

    public PageSelectStorage(Composite parent)
    {
        super(parent, SWT.NONE);

        createPage(this);

        _btnBack.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                traverse(SWT.TRAVERSE_PAGE_PREVIOUS);
            }
        });

        _btnNext.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                writeToModel(_model);
                traverse(SWT.TRAVERSE_PAGE_NEXT);
            }
        });
    }

    protected void createPage(Composite parent)
    {
        createHeader(parent);
        createContent(this);
        createButtonBar(parent);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);

        _compHeader.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _compContent.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        _compButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
    }

    protected void createHeader(Composite parent)
    {
        _compHeader = new Composite(parent, SWT.NONE);
        _compHeader.setBackgroundMode(SWT.INHERIT_FORCE);
        _compHeader.setBackground(SWTResourceManager.getColor(0xFF, 0xFF, 0xFF));

        _lblTitle = new Label(_compHeader, SWT.NONE);
        _lblTitle.setText(S.SETUP_TITLE);
        GUIUtil.changeFont(_lblTitle, 16, SWT.BOLD);

        // n.b. when rendering images on a label, setImage clears the alignment bits,
        //   hence we have to call setAlignment AFTER setImage to display image on the right
        _lblLogo = new Label(_compHeader, SWT.NONE);
        _lblLogo.setImage(Images.get(Images.IMG_SETUP));

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        _compHeader.setLayout(layout);

        GridData titleLayout = new GridData(SWT.LEFT, SWT.TOP, false, true);
        titleLayout.verticalIndent = 20;
        titleLayout.horizontalIndent = 20;
        _lblTitle.setLayoutData(titleLayout);
        _lblLogo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true));
    }

    protected void createContent(Composite parent)
    {
        _compContent = new Composite(parent, SWT.NONE);

        _lblMessage = new Label(_compContent, SWT.NONE);
        _lblMessage.setText(S.SETUP_STORAGE_MESSAGE);

        createChoices(_compContent);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.spacing = 30;
        layout.center = true;
        _compContent.setLayout(layout);
    }

    protected void createChoices(Composite parent)
    {
        _compChoices = new Composite(parent, SWT.NONE);

        _btnLocalStorage = new Button(_compChoices, SWT.RADIO);
        _btnLocalStorage.setText(S.SETUP_STORAGE_LOCAL);
        _btnLocalStorage.setFocus();

        _btnS3Storage = new Button(_compChoices, SWT.RADIO);
        _btnS3Storage.setText(S.SETUP_STORAGE_S3);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.spacing = 30;
        _compChoices.setLayout(layout);
    }

    protected void createButtonBar(Composite parent)
    {
        _compButtons = new Composite(parent, SWT.NONE);

        _btnBack = new Button(_compButtons, SWT.NONE);
        _btnBack.setText(S.BTN_BACK);

        _btnNext = new Button(_compButtons, SWT.NONE);
        _btnNext.setText(S.BTN_CONTINUE);
        getShell().setDefaultButton(_btnNext);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 0;
        layout.marginBottom = 20;
        layout.marginLeft = 0;
        layout.marginRight = 40;
        layout.center = true;
        _compButtons.setLayout(layout);

        _btnBack.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnNext.setLayoutData(new RowData(100, SWT.DEFAULT));
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

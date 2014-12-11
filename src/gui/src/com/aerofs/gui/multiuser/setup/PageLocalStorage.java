/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.ui.error.ErrorMessage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.createLabel;

public class PageLocalStorage extends AbstractSetupWorkPage
{
    private Text        _txtRootAnchor;
    private Button      _btnUseDefault;
    private Button      _btnChangeRootAnchor;

    private Button      _btnLinkStorage;
    private Button      _btnBlockStorage;

    private CompSpin    _compSpin;
    private Button      _btnInstall;
    private Button      _btnBack;

    static final String STORAGE_OPTIONS_URL = "https://support.aerofs.com/entries/23690567";

    public PageLocalStorage(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Composite compRootAnchor = createRootAnchorComposite(content);

        Label lblDescription = createLabel(content, SWT.NONE);
        lblDescription.setText(S.SETUP_TYPE_DESC);

        Composite compType = createTypeComposite(content);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.marginTop = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 10;
        content.setLayout(layout);

        compRootAnchor.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lblDescription.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        compType.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        return content;
    }

    protected Composite createRootAnchorComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblRootAnchor = createLabel(composite, SWT.NONE);
        lblRootAnchor.setText(S.SETUP_ROOT_ANCHOR_LABEL);

        _txtRootAnchor = new Text(composite, SWT.BORDER);
        _txtRootAnchor.setEditable(false);

        Composite compButtons = createRootAnchorButtons(composite);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 6;
        composite.setLayout(layout);

        lblRootAnchor.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        _txtRootAnchor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        compButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));

        return composite;
    }

    protected Composite createRootAnchorButtons(Composite parent)
    {
        Composite composite = GUIUtil.newButtonContainer(parent, false);

        _btnUseDefault = GUIUtil.createButton(composite, SWT.NONE);
        _btnUseDefault.setText(S.SETUP_USE_DEFAULT);
        _btnUseDefault.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                _txtRootAnchor.setText(_model._localOptions.getDefaultRootAnchorPath());
            }
        });

        _btnChangeRootAnchor = GUIUtil.createButton(composite, SWT.NONE);
        _btnChangeRootAnchor.setText(S.BTN_CHANGE);
        _btnChangeRootAnchor.setFocus();
        _btnChangeRootAnchor.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.SHEET);
                dialog.setMessage(S.SETUP_SELECT_ROOT_ANCHOR);

                String rootAnchor = dialog.open();
                if (rootAnchor != null) {
                    _txtRootAnchor.setText(RootAnchorUtil.adjustRootAnchor(rootAnchor, null));
                }
            }
        });

        _btnUseDefault.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnChangeRootAnchor.setLayoutData(new RowData(100, SWT.DEFAULT));

        return composite;
    }

    protected Composite createTypeComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        _btnLinkStorage = GUIUtil.createButton(composite, SWT.RADIO | SWT.WRAP);
        _btnLinkStorage.setText(S.SETUP_LINK);
        _btnLinkStorage.setFont(GUIUtil.makeBold(_btnLinkStorage.getFont()));

        Label lblLinkDesc = createLabel(composite, SWT.WRAP);
        lblLinkDesc.setText(S.SETUP_LINK_DESC);

        _btnBlockStorage = GUIUtil.createButton(composite, SWT.RADIO | SWT.WRAP);
        _btnBlockStorage.setText(S.SETUP_BLOCK);
        _btnBlockStorage.setFont(GUIUtil.makeBold(_btnBlockStorage.getFont()));

        Label lblBlockDesc = createLabel(composite, SWT.WRAP);
        lblBlockDesc.setText(S.SETUP_BLOCK_DESC);

        Link lnkLearnMore = new Link(composite, SWT.NONE);
        lnkLearnMore.setText(S.SETUP_STORAGE_LINK);
        lnkLearnMore.addSelectionListener(GUIUtil.createUrlLaunchListener(STORAGE_OPTIONS_URL));

        GridLayout layout = new GridLayout();
        layout.marginWidth = 5;
        layout.marginHeight = 0;
        layout.verticalSpacing = 6;
        layout.horizontalSpacing = 0;
        composite.setLayout(layout);

        _btnLinkStorage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lblLinkDesc.setLayoutData(createDescLayoutData());
        _btnBlockStorage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lblBlockDesc.setLayoutData(createDescLayoutData());
        lnkLearnMore.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));

        return composite;
    }

    protected GridData createDescLayoutData()
    {
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.horizontalIndent = 40;
        return data;
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        _compSpin = new CompSpin(parent, SWT.NONE);

        _btnBack = createButton(parent, S.BTN_BACK, false);
        _btnBack.addSelectionListener(createListenerToGoBack());

        _btnInstall = createButton(parent, S.SETUP_BTN_INSTALL, true);
        _btnInstall.addSelectionListener(createListenerToDoWork());
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtRootAnchor.setText(model._localOptions._rootAnchorPath);
        _btnLinkStorage.setSelection(!model._localOptions._useBlockStorage);
        _btnBlockStorage.setSelection(model._localOptions._useBlockStorage);
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._localOptions._rootAnchorPath = _txtRootAnchor.getText();
        model._localOptions._useBlockStorage = _btnBlockStorage.getSelection();
    }

    @Override
    protected @Nonnull Logger getLogger()
    {
        return Loggers.getLogger(PageLocalStorage.class);
    }

    @Override
    protected @Nonnull Button getDefaultButton()
    {
        return _btnInstall;
    }

    @Override
    protected Control[] getControls()
    {
        return new Control[] {
                _txtRootAnchor, _btnUseDefault, _btnChangeRootAnchor,
                _btnLinkStorage, _btnBlockStorage, _btnInstall, _btnBack
        };
    }

    @Override
    protected CompSpin getSpinner()
    {
        return _compSpin;
    }

    @Override
    protected void doWorkImpl() throws Exception
    {
        _model.doInstall();
    }

    @Override
    protected ErrorMessage[] getErrorMessages(Exception e)
    {
        return new ErrorMessage[] { new ErrorMessage(ExNoPerm.class, S.SETUP_NOT_ADMIN) };
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_DEFAULT_INSTALL_ERROR;
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.Images;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import java.net.ConnectException;

public class PageLocalStorage extends AbstractSetupPage
{
    private Logger l = Loggers.getLogger(PageLocalStorage.class);

    private boolean _inProgress;

    private Composite   _compContent;
    private Composite   _compRootAnchor;
    private Composite   _compRootAnchorButton;
    private Composite   _compType;
    private Composite   _compButton;

    private Label       _lblHeader;
    private Label       _lblRootAnchor;
    private Text        _txtRootAnchor;
    private Button      _btnUseDefault;
    private Button      _btnChangeRootAnchor;
    private Label       _lblDescription;
    private Button      _btnLink;
    private Label       _lblLinkDesc;
    private Button      _btnBlock;
    private Label       _lblBlockDesc;
    private CompSpin    _compSpin;
    private Button      _btnInstall;
    private Button      _btnBack;

    public PageLocalStorage(Composite parent)
    {
        super(parent, SWT.NONE);

        createPage();

        getShell().addListener(SWT.Close, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                if (_inProgress) event.doit = false;
            }
        });

        _btnUseDefault.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                _txtRootAnchor.setText(_model._localOptions.getDefaultRootAnchorPath());
            }
        });

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

        _btnBack.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                traverse(SWT.TRAVERSE_PAGE_PREVIOUS);
            }
        });

        _btnInstall.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                doWork();
            }
        });
    }

    protected void createPage()
    {
        createHeader(this);
        createContent(this);
        createButtonBar(this);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);

        _lblHeader.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _compContent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        _compButton.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
    }

    protected void createHeader(Composite parent)
    {
        // n.b. when rendering images on a label, setImage clears the alignment bits,
        //   hence we have to call setAlignment AFTER setImage to display image on the right
        _lblHeader = new Label(parent, SWT.NONE);
        _lblHeader.setBackground(new Color(getDisplay(), 0xFF, 0xFF, 0xFF));
        _lblHeader.setImage(Images.get(Images.IMG_SETUP));
        _lblHeader.setAlignment(SWT.RIGHT);
    }

    protected void createContent(Composite parent)
    {
        _compContent = new Composite(parent, SWT.NONE);

        createRootAnchorComposite(_compContent);

        _lblDescription = new Label(_compContent, SWT.NONE);
        _lblDescription.setText(S.SETUP_TYPE_DESC);

        createTypeComposite(_compContent);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 40;
        layout.marginHeight = 20;
        layout.verticalSpacing = 15;
        _compContent.setLayout(layout);

        _compRootAnchor.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _lblDescription.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        _compType.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    protected void createRootAnchorComposite(Composite parent)
    {
        _compRootAnchor = new Composite(parent, SWT.NONE);

        _lblRootAnchor = new Label(_compRootAnchor, SWT.NONE);
        _lblRootAnchor.setText(S.SETUP_ROOT_ANCHOR_LABEL);

        _txtRootAnchor = new Text(_compRootAnchor, SWT.BORDER);
        _txtRootAnchor.setEditable(false);

        createRootAnchorButtons(_compRootAnchor);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 0;
        _compRootAnchor.setLayout(layout);

        _lblRootAnchor.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        _txtRootAnchor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _compRootAnchorButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
    }

    protected void createRootAnchorButtons(Composite parent)
    {
        _compRootAnchorButton = new Composite(parent, SWT.NONE);

        _btnUseDefault = new Button(_compRootAnchorButton, SWT.NONE);
        _btnUseDefault.setText(S.SETUP_USE_DEFAULT);

        _btnChangeRootAnchor = new Button(_compRootAnchorButton, SWT.NONE);
        _btnChangeRootAnchor.setText(S.BTN_CHANGE);
        _btnChangeRootAnchor.setFocus();

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        _compRootAnchorButton.setLayout(layout);

        _btnUseDefault.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnChangeRootAnchor.setLayoutData(new RowData(100, SWT.DEFAULT));
    }

    protected void createTypeComposite(Composite parent)
    {
        _compType = new Composite(parent, SWT.NONE);

        _btnLink = new Button(_compType, SWT.RADIO | SWT.WRAP);
        _btnLink.setText(S.SETUP_LINK);

        _lblLinkDesc = new Label(_compType, SWT.WRAP);
        _lblLinkDesc.setText(S.SETUP_LINK_DESC);

        _btnBlock = new Button(_compType, SWT.RADIO | SWT.WRAP);
        _btnBlock.setText(S.SETUP_BLOCK);

        _lblBlockDesc = new Label(_compType, SWT.WRAP);
        _lblBlockDesc.setText(S.SETUP_BLOCK_DESC);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 5;
        layout.marginHeight = 0;
        layout.verticalSpacing = 10;
        _compType.setLayout(layout);

        GridData descLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
        descLayout.horizontalIndent = 18;
        GridData descLayout2 = new GridData(SWT.FILL, SWT.TOP, true, false);
        descLayout2.horizontalIndent = 18;
        _btnLink.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _lblLinkDesc.setLayoutData(descLayout);
        _btnBlock.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _lblBlockDesc.setLayoutData(descLayout2);
    }

    protected void createButtonBar(Composite parent)
    {
        _compButton = new Composite(parent, SWT.NONE);

        _compSpin = new CompSpin(_compButton, SWT.NONE);

        _btnBack = new Button(_compButton, SWT.NONE);
        _btnBack.setText(S.BTN_BACK);

        _btnInstall = new Button(_compButton, SWT.NONE);
        _btnInstall.setText(S.SETUP_BTN_INSTALL);
        getShell().setDefaultButton(_btnInstall);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 0;
        layout.marginBottom = 20;
        layout.marginLeft = 0;
        layout.marginRight = 40;
        layout.center = true;
        _compButton.setLayout(layout);

        _btnBack.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnInstall.setLayoutData(new RowData(100, SWT.DEFAULT));
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtRootAnchor.setText(model._localOptions._rootAnchorPath);
        _btnLink.setSelection(!model._localOptions._useBlockStorage);
        _btnBlock.setSelection(model._localOptions._useBlockStorage);
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._localOptions._rootAnchorPath = _txtRootAnchor.getText();
        model._localOptions._useBlockStorage = _btnLink.getSelection();
    }

    private void setProgress(boolean inProgress)
    {
        _inProgress = inProgress;

        if (_inProgress) {
            updateStatus("", true, false);
            setControlState(false);
        } else {
            updateStatus("", false, false);
            setControlState(true);
        }
    }

    private void setControlState(boolean enable)
    {
        _txtRootAnchor.setEnabled(enable);
        _btnUseDefault.setEnabled(enable);
        _btnChangeRootAnchor.setEnabled(enable);
        _btnLink.setEnabled(enable);
        _btnBlock.setEnabled(enable);
        _btnInstall.setEnabled(enable);
        _btnBack.setEnabled(enable);
    }

    private void updateStatus(String status, boolean indicateProgress, boolean indicateError)
    {
        if (indicateProgress) _compSpin.start();
        else if (indicateError) _compSpin.error();
        else _compSpin.stop();
    }

    private void doWork()
    {
        setProgress(true);

        writeToModel(_model);

        GUI.get().safeWork(_btnInstall, new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                _model.install();
            }

            @Override
            public void okay()
            {
                setProgress(false);
                traverse(SWT.TRAVERSE_PAGE_NEXT);
            }

            @Override
            public void error(Exception e)
            {
                l.error("Setup error", e);
                GUI.get().show(getShell(), MessageType.ERROR, formatException(e));
                setProgress(false);
            }

            private String formatException(Exception e)
            {
                if (e instanceof ConnectException) return S.SETUP_ERR_CONN;
                else if (e instanceof ExUIMessage) return e.getMessage();
                else if (e instanceof ExBadCredential) return S.BAD_CREDENTIAL_CAP + '.';
                else return "Sorry, " + UIUtil.e2msgNoBracket(e) + '.';
            }
        });
    }
}

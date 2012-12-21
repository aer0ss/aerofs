package com.aerofs.gui.setup;

import java.io.File;

import com.aerofs.ui.UI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;

import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

//public class DlgSetupAdvanced extends org.eclipse.jface.dialogs.Dialog {
public class DlgSetupAdvanced extends AeroFSJFaceDialog {

    private Text _txtDeviceName;
    private String _deviceName;
    private String _absRootAnchor;
    private Text _txtRoot;

    public DlgSetupAdvanced(Shell parentShell, String deviceName, String absRootAnchor)
    {
        super("Advanced Options", parentShell, true, false, false, false);
        _deviceName = deviceName;
        _absRootAnchor = absRootAnchor;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        gridLayout.marginRight = 20;
        gridLayout.marginTop = 20;
        gridLayout.marginLeft = 20;
        container.setLayout(gridLayout);

        Label lblComputerName = new Label(container, SWT.NONE);
        lblComputerName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblComputerName.setText(S.SETUP_DEV_ALIAS + ":");

        _txtDeviceName = new Text(container, SWT.BORDER);
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _txtDeviceName.setText(_deviceName);
        new Label(container, SWT.NONE);

        new Label(container, SWT.NONE);

        Label lbl = new Label(container, SWT.NONE);
        lbl.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lbl.setText(S.ROOT_ANCHOR + ":");

        Composite _composite = new Composite(container, SWT.NONE);
        GridLayout glComposite = new GridLayout(2, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        _composite.setLayout(glComposite);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1));

        _txtRoot = new Text(_composite, SWT.BORDER | SWT.READ_ONLY);
        _txtRoot.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Button btnChangeLocation = new Button(_composite, SWT.NONE);
        btnChangeLocation.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
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

        Button btnUserDefaultLocation = new Button(_composite, SWT.NONE);
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
        btnUserDefaultLocation.setText("User Default");

        getShell().addListener(SWT.Show, new Listener() {

            @Override
            public void handleEvent(Event event)
            {
                _txtRoot.setText(_absRootAnchor);
            }
        });

        return container;
    }

    /**
     * Create contents of the button bar
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        _deviceName = _txtDeviceName.getText();
        _absRootAnchor = _txtRoot.getText();
        super.buttonPressed(buttonId);
    }

    public String getDeviceName()
    {
        return _deviceName;
    }

    public String getAbsoluteRootAnchor()
    {
        assert new File(_absRootAnchor).isAbsolute();
        return _absRootAnchor;
    }
}

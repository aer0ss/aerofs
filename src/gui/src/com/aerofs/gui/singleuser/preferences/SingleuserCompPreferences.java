/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.singleuser.preferences;

import com.aerofs.gui.GUI;
import com.aerofs.gui.exclusion.DlgExclusion;
import com.aerofs.gui.preferences.PreferencesHelper;
import com.aerofs.gui.transfers.DlgThrottling;
import com.aerofs.gui.transfers.DlgTransfers;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public class SingleuserCompPreferences extends Composite
{
    private final Button _btnNotify;
    private final Label _lblId2;
    private final Label _lblId;
    private boolean _deviceIDShown;
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public SingleuserCompPreferences(Composite parent, boolean showTransfers)
    {
        super(parent, SWT.NONE);

        PreferencesHelper helper = new PreferencesHelper(this);
        helper.setLayout();

        // Id row

        _lblId = new Label(this, SWT.RIGHT);
        _lblId.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblId.setText("User ID:");

        _lblId2 = new Label(this, SWT.NONE);
        _lblId2.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        _lblId2.setText(Cfg.user().getString());
        _lblId2.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent e)
            {
                if (_deviceIDShown) {
                    _lblId.setText("User ID:");
                    _lblId2.setText(Cfg.user().getString());
                } else {
                    _lblId.setText("Computer ID:");
                    _lblId2.setText(Cfg.did().toStringFormal());
                }
                _deviceIDShown = !_deviceIDShown;
                layout(new Control[]{_lblId2, _lblId});
            }
        });

        // First name row

        helper.createFirstNameLabelAndText();

        // Last name row

        helper.createLastNameLabelAndText();

        // Device name row

        helper.createDeviceNameLabelAndText();

        // Root anchor relocation row

        helper.createRelocationLabelAndText();

        // Show notifications row

        new Label(this, SWT.NONE);

        _btnNotify = new Button(this, SWT.CHECK);
        _btnNotify.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        _btnNotify.setText("Notify me about file changes");
        _btnNotify.setSelection(Cfg.db().getBoolean(Key.NOTIFY));
        _btnNotify.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setDC(_btnNotify.getSelection());
            }
        });

        // Spinner row

        helper.createSpinner();

        // Separator row

        Label label_1 = new Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
                label_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));

        // Empty row

        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);

        // Buttons row

        new Label(this, SWT.NONE);
        Composite btnsComposite = new Composite(this, SWT.NONE);
        createButtons(btnsComposite, showTransfers);
        new Label(this, SWT.NONE);

        helper.registerShellListeners();
    }

    private void createButtons(Composite composite, boolean showTransfers)
    {
        GridLayout gl_composite_1 = new GridLayout(1, false);
        gl_composite_1.marginLeft = OSUtil.isOSX() ? -6 : 0;
        gl_composite_1.verticalSpacing = OSUtil.isOSX() ? -2 : gl_composite_1.verticalSpacing;
        gl_composite_1.marginWidth = 0;
        gl_composite_1.marginHeight = 0;
        composite.setLayout(gl_composite_1);
        composite.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

        Button btnSelectiveSync = new Button(composite, SWT.NONE);
        btnSelectiveSync.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnSelectiveSync.setText("Selective Sync...");
        btnSelectiveSync.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new DlgExclusion(getShell()).openDialog();
            }
        });

        Button btnBandwidth = new Button(composite, SWT.NONE);
        btnBandwidth.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnBandwidth.setText("Limit Bandwidth...");
        btnBandwidth.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                new DlgThrottling(getShell(), true).openDialog();
            }
        });

        if (showTransfers) {
            Button btnShowTransfers = new Button(composite, SWT.NONE);
            btnShowTransfers.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
            btnShowTransfers.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent arg0)
                {
                    new DlgTransfers(GUI.get().sh()).openDialog();
                }
            });
            btnShowTransfers.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
            btnShowTransfers.setText("Show Transfers...");
        }

        new Label(composite, SWT.NONE);

        Button button = new Button(composite, SWT.NONE);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        button.setText(S.UNLINK_THIS_COMPUTER);
        button.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                if (GUI.get().ask(getShell(), MessageType.WARN, S.UNLINK_THIS_COMPUTER_CONFIRM)) {
                    try {
                        SVClient.sendEventAsync(Type.UNLINK);
                        RockLog.newEvent("Unlink Device").sendAsync();
                        UIUtil.unlinkAndExit(_factFile);
                    } catch (Exception e) {
                        GUI.get()
                                .show(MessageType.ERROR,
                                        "Couldn't unlink the computer " + UIUtil.e2msg(e));
                    }
                }
            }
        });
    }

    private void setDC(Boolean notify)
    {
        try {
            if (notify != null) {
                Cfg.db().set(Key.NOTIFY, notify);
            }
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR, "Couldn't change settings "
                    + UIUtil.e2msg(e));
        }
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}

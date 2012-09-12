package com.aerofs.gui.preferences;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.exclusion.DlgExclusion;
import com.aerofs.gui.password.DlgPasswordChange;
import com.aerofs.gui.transfers.DlgThrottling;
import com.aerofs.gui.transfers.DlgTransfers;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;
import com.aerofs.proto.Sp.GetPreferencesReply;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import javax.annotation.Nullable;

public class CompPreferences extends Composite
{
    private final static Logger l = Util.l(CompPreferences.class);

    private final Button _btnNotify;
    private final Label _lblId2;
    private final Text _txtDeviceName;
    private final CompSpin _compSpin;
    private final Label _lblId;
    private boolean _deviceIDShown;
    private final Text _txtFirstName;
    private final Text _txtLastName;
    private final Text _txtRootAnchor;
    private String _firstName;  // must be null initially. see updateUserAndDeviceName
    private String _lastName;   // must be null initially. see updateUserAndDeviceName
    private String _deviceName; // must be null initially. see updateUserAndDeviceName
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public CompPreferences(Composite parent, boolean showTransfers)
    {
        super(parent, SWT.NONE);

        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 10;
        this.setLayout(gridLayout);

        // Id row

        _lblId = new Label(this, SWT.RIGHT);
        _lblId.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblId.setText("User ID:");

        _lblId2 = new Label(this, SWT.NONE);
        _lblId2.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        _lblId2.setText(Cfg.user());
        _lblId2.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent e)
            {
                if (_deviceIDShown) {
                    _lblId.setText("User ID:");
                    _lblId2.setText(Cfg.user());
                } else {
                    _lblId.setText("Computer ID:");
                    _lblId2.setText(Cfg.did().toStringFormal());
                }
                _deviceIDShown = !_deviceIDShown;
                layout(new Control[]{_lblId2, _lblId});
            }
        });

        // First name row

        Label lblFirstName = new Label(this, SWT.NONE);
        lblFirstName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblFirstName.setText(S.SETUP_FIRST_NAME + ":");

        _txtFirstName = new Text(this, SWT.BORDER);
        GridData gd_txtFirstName = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_txtFirstName.widthHint = 120;
        _txtFirstName.setLayoutData(gd_txtFirstName);
        _txtFirstName.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent focusEvent)
            {
                updateUserAndDeviceName(_txtFirstName);
            }
        });

        new Label(this, SWT.NONE);

        // Last name row

        Label lblLastName = new Label(this, SWT.NONE);
        lblLastName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblLastName.setText(S.SETUP_LAST_NAME + ":");

        _txtLastName = new Text(this, SWT.BORDER);
        GridData gd_txtLastName = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_txtLastName.widthHint = 120;
        _txtLastName.setLayoutData(gd_txtLastName);
        _txtLastName.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent focusEvent)
            {
                updateUserAndDeviceName(_txtLastName);
            }
        });

        new Label(this, SWT.NONE);

        // Device name row

        Label lblDeviceName = new Label(this, SWT.NONE);
        lblDeviceName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblDeviceName.setText("Computer name:");

        _txtDeviceName = new Text(this, SWT.BORDER);
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        _txtDeviceName.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent focusEvent)
            {
                updateUserAndDeviceName(_txtDeviceName);
            }
        });

        new Label(this, SWT.NONE);

        // Root anchor location row

        Label lblAerofsLocation = new Label(this, SWT.NONE);
        lblAerofsLocation.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblAerofsLocation.setText(S.PRODUCT + " location:");

        _txtRootAnchor = new Text(this, SWT.BORDER | SWT.READ_ONLY);
        GridData gd__txtRootAnchor = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd__txtRootAnchor.widthHint = 100;
        _txtRootAnchor.setLayoutData(gd__txtRootAnchor);
        _txtRootAnchor.setText(Cfg.absRootAnchor());

        Button btnMoveRoot = new Button(this, SWT.NONE);
        btnMoveRoot.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
        btnMoveRoot.setText("Move...");
        btnMoveRoot.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                selectAndMoveRootAnchor_();
            }
        });

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

        // Empty row with the spinning indicator on the left

        _compSpin = new CompSpin(this, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));

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

        getShell().addListener(SWT.Show, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                initAsync();
            }
        });

        // Save settings on close
        getShell().addShellListener(new ShellAdapter()
        {
            @Override
            public void shellClosed(ShellEvent shellEvent)
            {
                updateUserAndDeviceName(null);
            }
        });
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

        Button btnChangePassword;
        if (Cfg.staging()) {
            btnChangePassword = new Button(composite, SWT.NONE);
            btnChangePassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
            btnChangePassword.setText("Change Password...");
            btnChangePassword.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    DlgPasswordChange dPC = new DlgPasswordChange(getShell());
                    if (dPC.open() == IDialogConstants.OK_ID) {
                        String new_password = dPC.getPassword();
                        String old_password = dPC.getOldPassword();
                        try {
                            UI.controller().changePassword(Cfg.user(),old_password,new_password);
                            GUI.get().show(getShell(), MessageType.INFO,
                                    "Password Changed Successfully!");
                        } catch (Exception e) {
                            l.warn(Util.e(e));
                            GUI.get().show(getShell(), MessageType.ERROR,
                                    "Couldn't change password. Please try again.");
                        }
                    }
                }
            });
        }

        Button btnShowTransfers;
        if (showTransfers) {
            btnShowTransfers = new Button(composite, SWT.NONE);
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
        } else {
            btnShowTransfers = null;
        }

        new Label(composite, SWT.NONE);

        Button button = new Button(composite, SWT.NONE);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        button.setText("Unlink This Computer");
        button.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                if (GUI.get().ask(getShell(), MessageType.WARN, S.UNLINK_THIS_COMPUTER_CONFIRM)) {
                    try {
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

    public void initAsync()
    {
        // set field values on SHOW instead of the ctor to avoid the
        // dialog stretched
        _txtRootAnchor.setText(Cfg.absRootAnchor());
        _txtDeviceName.setEditable(false);
        _txtFirstName.setEditable(false);
        _txtLastName.setEditable(false);

        _compSpin.start();

        // TODO (GS): This code would be much nicer using SP non-blocking client, but first we need
        // to make SPClientHandler.doRPC() asynchronous (using Netty)
        Thread thd = new Thread() {
            @Override
            public void run()
            {
                Exception e = null;
                GetPreferencesReply r = null;
                try {
                    SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
                    sp.signInRemote();
                    r = sp.getPreferences(Cfg.did().toPB());
                } catch (ExBadCredential ebc) {
                    l.warn(Util.e(ebc));
                } catch (Exception e2) {
                    l.warn(Util.e(e2));
                    e = e2;
                }

                final GetPreferencesReply reply = r;
                final Exception eFinal = e;
                GUI.get().safeAsyncExec(CompPreferences.this, new Runnable() {
                    @Override
                    public void run()
                    {
                        if (eFinal != null) {
                            GUI.get().show(getShell(), MessageType.ERROR, UIUtil.e2msg(eFinal));
                        } else if (reply != null) {
                            _txtFirstName.setText(reply.getFirstName());
                            _txtFirstName.setEditable(true);
                            _txtLastName.setText(reply.getLastName());
                            _txtLastName.setEditable(true);
                            _txtDeviceName.setText(reply.getDeviceName());
                            _txtDeviceName.setEditable(true);

                            _firstName = _txtFirstName.getText();
                            _lastName = _txtLastName.getText();
                            _deviceName = _txtDeviceName.getText();
                        }
                        _compSpin.stop();
                    }
                });
            }
        };
        thd.setDaemon(true);
        thd.start();
    }

    private void updateUserAndDeviceName(@Nullable final Text selectAll)
    {
        // the user name is updated before we successfully fetched the
        // profile. ignore the request in the case.
        if (_firstName == null || _lastName == null || _deviceName == null) return;

        boolean changed = false;

        // If either first or last name changed, set both
        String firstName = null;
        String lastName = null;
        if (!_txtFirstName.getText().equals(_firstName)
                || !_txtLastName.getText().equals(_lastName)) {
            firstName = _txtFirstName.getText();
            lastName = _txtLastName.getText();
            changed = true;
        }

        final String firstNameToSend = firstName;
        final String lastNameToSend = lastName;

        final String deviceName = _txtDeviceName.getText();
        final String deviceNameToSend = deviceName.equals(_deviceName) ? null : deviceName;
        changed |= (deviceNameToSend != null);

        if (!changed) return;

        _compSpin.start();

        Util.startDaemonThread("update-names", new Runnable() {
            @Override
            public void run()
            {
                Exception e;
                try {
                    SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
                    sp.signInRemote();
                    sp.setPreferences(firstNameToSend, lastNameToSend,
                            (deviceNameToSend != null) ? Cfg.did().toPB() : null, deviceNameToSend);
                    e = null;
                } catch (Exception e2) {
                    e = e2;
                }

                final Exception eFinal = e;
                GUI.get().safeAsyncExec(CompPreferences.this, new Runnable() {
                    @Override
                    public void run()
                    {
                        _compSpin.stop();

                        if (eFinal != null) {
                            GUI.get().show(getShell(), MessageType.ERROR,
                                    "Couldn't update the name " + UIUtil.e2msg(eFinal));
                        } else {
                            _firstName = _txtFirstName.getText();
                            _lastName = _txtLastName.getText();
                            _deviceName = deviceName;
                            if (selectAll != null) selectAll.selectAll();
                        }
                    }
                });
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

    private void selectAndMoveRootAnchor_()
    {
        // Have to re-open the directory dialog in a separate stack, since doing it in the same
        // stack would cause strange SWT crashes on OSX :/

        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run()
            {
                DirectoryDialog dd = new DirectoryDialog(getShell(), SWT.SHEET);
                dd.setMessage("Select " + S.SETUP_ANCHOR_ROOT);
                String root = dd.open();
                if (root != null && moveAnchorRoot(root)) selectAndMoveRootAnchor_();
            }
        });
    }

    /**
     * @return whether to reopen the select-folder dialog.
     */
    private boolean moveAnchorRoot(String rootParent)
    {
        String pathOld = Cfg.absRootAnchor();
        String pathNew = RootAnchorUtil.adjustRootAnchor(rootParent);

        try {
            RootAnchorUtil.checkNewRootAnchor(pathOld, pathNew);
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.WARN, e.getMessage() +
                    ". Please select a different folder.");
            return true;
        }

        if (!GUI.get().ask(getShell(), MessageType.QUESTION, "Are you sure you want to move the "
                + S.PRODUCT + " folder and its content from:\n\n" + pathOld + " \n\n to: \n\n " +
                pathNew + "?")) {
            return false;
        }

        DlgMoveRootAnchor dlg = new DlgMoveRootAnchor(getShell(), true, pathNew);

        Boolean okay = (Boolean) dlg.openDialog();
        if (okay != null && okay) {
            _txtRootAnchor.setText(pathNew);
            return false;
        } else {
            return true;
        }
    }
}

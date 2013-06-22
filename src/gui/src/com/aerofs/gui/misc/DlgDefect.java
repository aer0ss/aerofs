package com.aerofs.gui.misc;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.shell.CmdDefect;
import com.aerofs.ui.UIGlobals;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;

import static com.aerofs.gui.GUIUtil.getNewText;

public class DlgDefect extends AeroFSJFaceDialog
{
    private static final Logger l = Loggers.getLogger(DlgDefect.class);

    private static interface IContactEmailGetter
    {
        // must be called from the GUI thread
        String get();
    }

    private IContactEmailGetter _contactEmailGetter;
    private Text _txtReport;
    private Button _sendDiagnosticData;

    public DlgDefect(Shell parentShell)
    {
        super(S.REPORT_A_PROBLEM, parentShell, false, true, true, true);
    }

    /**
     * Create contents of the dialog.
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);

        GridLayout gl_container = new GridLayout(1, false);
        gl_container.verticalSpacing = GUIParam.MAJOR_SPACING / 2;
        gl_container.marginTop = GUIParam.MARGIN;
        gl_container.marginHeight = 0;
        gl_container.marginWidth = GUIParam.MARGIN;
        container.setLayout(gl_container);

        createEmailComposite(container);

        Label lblWhatsUp = new Label(container, SWT.NONE);
        lblWhatsUp.setText("What is the problem?");

        _txtReport = new Text(container, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_text.heightHint = 87;
        gd_text.widthHint = 346;
        _txtReport.setLayoutData(gd_text);

        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        GridLayout gl_composite = new GridLayout(2, false);
        gl_composite.marginHeight = 0;
        gl_composite.marginWidth = 0;
        composite.setLayout(gl_composite);

        _sendDiagnosticData = new Button(composite, SWT.CHECK);
        _sendDiagnosticData.setSelection(true);
        _sendDiagnosticData.setText("Send metadata (including file names)");

        if (L.isMultiuser()) {
            // no plaintext file names can be collected with multiuser installs
            _sendDiagnosticData.setVisible(false);
        }

        Link link = new Link(composite, SWT.NONE);
        link.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        link.setText("<a>What do we collect?</a>");
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                GUIUtil.launch(WWW.TOS_URL.get());
            }
        });

        getShell().addShellListener(new ShellAdapter()
        {
            @Override
            public void shellActivated(ShellEvent shellEvent)
            {
                verify(null);
            }
        });

        return container;
    }

    private void createEmailComposite(Composite container)
    {
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayout(gridLayout);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label lblYouEmail = new Label(composite, SWT.NONE);
        lblYouEmail.setText("Your email:");

        GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);

        if (L.isMultiuser()) {
            final Text txtEmailAddress = new Text(composite, SWT.BORDER);
            txtEmailAddress.setLayoutData(gridData);
            txtEmailAddress.setText(Cfg.db().get(Key.MULTIUSER_CONTACT_EMAIL));

            txtEmailAddress.addVerifyListener(new VerifyListener()
            {
                @Override
                public void verifyText(VerifyEvent verifyEvent)
                {
                    verify(getNewText(txtEmailAddress, verifyEvent));
                }
            });

            _contactEmailGetter = new IContactEmailGetter()
            {
                @Override
                public String get()
                {
                    return txtEmailAddress.getText();
                }
            };

        } else {
            final String emailAddress =  Cfg.user().getString();
            Label lblEmailAddress = new Label(composite, SWT.NONE);
            lblEmailAddress.setLayoutData(gridData);
            lblEmailAddress.setText(emailAddress);
            _contactEmailGetter = new IContactEmailGetter()
            {
                @Override
                public String get()
                {
                    return emailAddress;
                }
            };
        }
    }

    private void verify(@Nullable String contactEmail)
    {
        if (contactEmail == null) contactEmail = _contactEmailGetter.get();
        getButton(IDialogConstants.OK_ID).setEnabled(Util.isValidEmailAddress(contactEmail));
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            final String msg = _txtReport.getText();
            final boolean dumpDaemonStatus = _sendDiagnosticData.getSelection();
            final String contactEmail = _contactEmailGetter.get();

            ThreadUtil.startDaemonThread("defect-sender", new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        Cfg.db().set(Key.MULTIUSER_CONTACT_EMAIL, contactEmail);
                    } catch (SQLException e) {
                        l.warn("set contact email, ignored: " + Util.e(e));
                    }

                    CmdDefect.sendDefect(UIGlobals.ritual(), msg, dumpDaemonStatus);
                }
            });
        }

        super.buttonPressed(buttonId);
    }

    /**
     * Create contents of the button bar.
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, "Report", true);
    }
}

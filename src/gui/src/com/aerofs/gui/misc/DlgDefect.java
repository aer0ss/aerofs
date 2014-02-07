package com.aerofs.gui.misc;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUI;
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
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
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

public class DlgDefect extends AeroFSJFaceDialog
{
    private static final Logger l = Loggers.getLogger(DlgDefect.class);
    private Text _txtEmailAddress;
    private Text _txtComment;
    private Button _sendDiagnosticData;

    @Nullable private final Throwable _exception;
    private final boolean _expectedException;

    public DlgDefect()
    {
        this(null, null, false);
    }

    /**
     *
     * @param sheetStyleParent if non-null, the dialog attaches to this shell with the SHEET style.
     * @param exception if non-null, the dialog shows the exception's stack as technical
     * details, and the comment is optional.
     * @param expectedException if {@code exception} is non-null, true means the exception is one of
     * the expected exception types specified in ErrorMessage.show(). In this case, the string
     * "(expected error)" will be attached to the support email. Support personnel can ignore
     * emails with expected exceptions unless the user provides additional information.
     */
    public DlgDefect(@Nullable Shell sheetStyleParent, @Nullable Throwable exception,
            boolean expectedException)
    {
        super(S.REPORT_A_PROBLEM, sheetStyleParent == null ? GUI.get().sh() : sheetStyleParent,
                sheetStyleParent != null, true, true, true);
        _exception = exception;
        _expectedException = expectedException;
    }

    /**
     * Create contents of the dialog.
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);

        GridLayout gl_container = new GridLayout(1, false);
        gl_container.verticalSpacing = GUIParam.VERTICAL_SPACING;
        gl_container.marginTop = GUIParam.MARGIN;
        gl_container.marginHeight = 0;
        gl_container.marginWidth = GUIParam.MARGIN;
        container.setLayout(gl_container);

        createEmailFields(container);

        createCommentFields(container);

        if (_exception != null) createExceptionDetailsFields(container);

        createSendMetadataFields(container);

        getShell().addShellListener(new ShellAdapter()
        {
            @Override
            public void shellActivated(ShellEvent shellEvent)
            {
                updateControlStatus();
            }
        });

        _txtComment.setFocus();

        return container;
    }

    private void createCommentFields(Composite container)
    {
        Label lblWhatsUp = new Label(container, SWT.NONE);
        // \n: a nasty way of setting margins. it's ugly but it works.
        String msg = "\nPlease describe the problem:\n" +
                "\t- What are you trying to accomplish?\n" +
                "\t- How do you expect AeroFS to behave?\n" +
                "\t- What do you see AeroFS doing instead?";
        lblWhatsUp.setText(msg);

        _txtComment = new Text(container, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_text.heightHint = 80;
        gd_text.widthHint = 346;
        _txtComment.setLayoutData(gd_text);

        _txtComment.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                updateControlStatus();
            }
        });
    }

    private void createExceptionDetailsFields(Composite container)
    {
        Label lblWhatsUp = new Label(container, SWT.NONE);
        // \n: a nasty way of setting margins. it's ugly but it works.
        lblWhatsUp.setText("\nTechnical details:");

        Text txtDetails = new Text(container, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI |
                SWT.READ_ONLY);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_text.heightHint = 120;
        gd_text.widthHint = 346;
        txtDetails.setLayoutData(gd_text);

        txtDetails.setForeground(GUI.get().disp().getSystemColor(SWT.COLOR_DARK_GRAY));
        txtDetails.setText(ExceptionUtils.getFullStackTrace(_exception));
    }

    private void createSendMetadataFields(Composite container)
    {
        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        GridLayout gl_composite = new GridLayout(2, false);
        gl_composite.marginHeight = 0;
        gl_composite.marginWidth = 0;
        composite.setLayout(gl_composite);

        _sendDiagnosticData = GUIUtil.createButton(composite, SWT.CHECK);
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
                GUIUtil.launch(WWW.TOS_URL);
            }
        });
    }

    private void createEmailFields(Composite container)
    {
        Label lblWhatsUp = new Label(container, SWT.NONE);
        lblWhatsUp.setText(
                "Thank you for contacting us! We will get back to you as early as we can.\n" +
                "This email address will be used for correspondence regarding this issue:");

        _txtEmailAddress = new Text(container, SWT.BORDER);
        _txtEmailAddress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _txtEmailAddress.setText(Cfg.db().get(Key.CONTACT_EMAIL));
        _txtEmailAddress.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                updateControlStatus();
            }
        });
    }

    private void updateControlStatus()
    {
        boolean ready = Util.isValidEmailAddress(_txtEmailAddress.getText())
                && !_txtComment.getText().trim().isEmpty();
        getButton(IDialogConstants.OK_ID).setEnabled(ready);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) sendDefect();

        super.buttonPressed(buttonId);
    }

    private void sendDefect()
    {
        final String msg = _txtComment.getText() + (_exception == null ? "" :
                 "\n\nTechical detail (" +
                 (_expectedException ? "" : "un") +
                 "expected error):\n" + ExceptionUtils.getFullStackTrace(_exception));

        final boolean dumpDaemonStatus = _sendDiagnosticData.getSelection();
        final String contactEmail = _txtEmailAddress.getText();

        ThreadUtil.startDaemonThread("defect-sender", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Cfg.db().set(Key.CONTACT_EMAIL, contactEmail);
                } catch (SQLException e) {
                    l.warn("set contact email, ignored: " + Util.e(e));
                }

                CmdDefect.sendDefect(UIGlobals.ritual(), msg, dumpDaemonStatus);
            }
        });
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

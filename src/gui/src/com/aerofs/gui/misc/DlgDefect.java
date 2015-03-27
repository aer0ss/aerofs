package com.aerofs.gui.misc;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.defects.UIPriorityDefect.Factory;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.UIGlobals;
import com.swtdesigner.SWTResourceManager;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import javax.annotation.Nullable;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.lib.cfg.ICfgStore.CONTACT_EMAIL;

public class DlgDefect extends AeroFSJFaceDialog
{
    // FIXME(AT): find a better way to do this
    private static final Factory _defectFactory = new Factory(UIGlobals.ritualClientProvider());

    private Text _txtEmailAddress;
    private Text _txtSubject;
    private Text _txtMessage;

    @Nullable private final Throwable _exception;

    public DlgDefect()
    {
        this(null, null);
    }

    public DlgDefect(@Nullable Shell sheetStyleParent, @Nullable Throwable exception)
    {
        super(S.REPORT_A_PROBLEM, sheetStyleParent == null ? GUI.get().sh() : sheetStyleParent,
                sheetStyleParent != null, true, true, true);
        _exception = exception;
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

        _txtSubject.setFocus();

        return container;
    }

    private void createCommentFields(Composite container)
    {
        Label lblTxtSubject = createLabel(container, SWT.NONE);
        lblTxtSubject.setText("\nSubject:");

        _txtSubject = new Text(container, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
        GridData gdSubject = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gdSubject.heightHint = 20;
        gdSubject.widthHint = 346;
        _txtSubject.setLayoutData(gdSubject);
        _txtSubject.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                updateControlStatus();
            }
        });

        Label lblTxtMessage = createLabel(container, SWT.NONE);
        lblTxtMessage.setText("\nMessage (please describe the problem):");

        _txtMessage = new Text(container, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
        GridData gdMessage = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gdMessage.heightHint = 80;
        gdMessage.widthHint = 346;
        _txtMessage.setLayoutData(gdMessage);
        _txtMessage.addModifyListener(new ModifyListener()
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
        Label lblWhatsUp = createLabel(container, SWT.NONE);
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
        Label lblEmail = createLabel(container, SWT.NONE);
        lblEmail.setText("This email address will be used for correspondence regarding this issue:");

        _txtEmailAddress = new Text(container, SWT.BORDER);
        _txtEmailAddress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _txtEmailAddress.setText(Cfg.db().get(CONTACT_EMAIL));
        _txtEmailAddress.setEditable(false);
        _txtEmailAddress.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
    }

    private void updateControlStatus()
    {
        boolean ready =
                !_txtSubject.getText().trim().isEmpty() &&
                !_txtMessage.getText().trim().isEmpty();

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
        // Strip extra spaces and newlines from the message.
        String subject = _txtSubject.getText().replaceAll("\\s+", " ");
        String message = _txtMessage.getText().replaceAll("\\s+", " ");

        String contactEmail = _txtEmailAddress.getText();

        _defectFactory.newPriorityDefect()
                .setSubject(subject)
                .setMessage(message)
                .setException(_exception)
                .setContactEmail(contactEmail)
                .sendAsync();
    }

    /**
     * Create contents of the button bar.
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, "Report", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
}

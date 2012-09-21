package com.aerofs.gui.misc;

import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.shell.CmdDefect;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Link;

public class DlgDefect extends AeroFSJFaceDialog {

    private Text _txt;
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

        Label lblFrom = new Label(container, SWT.NONE);
        lblFrom.setText("From: " + Cfg.user());

        Label lblWhatsUp = new Label(container, SWT.NONE);
        lblWhatsUp.setText("What is the problem?");

        _txt = new Text(container, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.MULTI);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_text.heightHint = 87;
        gd_text.widthHint = 346;
        _txt.setLayoutData(gd_text);

        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        GridLayout gl_composite = new GridLayout(2, false);
        gl_composite.marginHeight = 0;
        gl_composite.marginWidth = 0;
        composite.setLayout(gl_composite);

        _sendDiagnosticData = new Button(composite, SWT.CHECK);
        _sendDiagnosticData.setSelection(true);
        _sendDiagnosticData.setText("Send diagnostic data");

        Link link = new Link(composite, SWT.NONE);
        link.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        link.setText("<a>What do we collect?</a>");
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                Program.launch(S.PRIVACY_URL);
            }
        });

        return container;
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            final String msg = _txt.getText();
            final boolean dumpDaemonStatus = _sendDiagnosticData.getSelection();

            Util.startDaemonThread("defect-sender", new Runnable()
            {
                @Override
                public void run()
                {
                    RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                    try {
                        CmdDefect.sendDefect(ritual, msg, dumpDaemonStatus);
                    } finally {
                        ritual.close();
                    }
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

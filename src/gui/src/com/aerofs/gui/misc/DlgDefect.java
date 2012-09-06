package com.aerofs.gui.misc;

import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.C;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.IUI.MessageType;

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
    private Button _btnSubmitData;
    private static String s_failedMessage;

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
        if (s_failedMessage != null) {
            _txt.setText(s_failedMessage);
            s_failedMessage = null;
        }

        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        GridLayout gl_composite = new GridLayout(2, false);
        gl_composite.marginHeight = 0;
        gl_composite.marginWidth = 0;
        composite.setLayout(gl_composite);

        _btnSubmitData = new Button(composite, SWT.CHECK);
        _btnSubmitData.setSelection(true);
        _btnSubmitData.setText("Send diagnostic data");

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

    void submit(final String message, final boolean verbose)
    {
        Thread thd = new Thread() {
            @Override
            public void run()
            {
                boolean cpuIssue = message.toLowerCase().contains("cpu");

                Object prog = UI.get().addProgress(cpuIssue ? "Sampling " + S.PRODUCT +
                        " CPU usage" : "Submitting", true);
                try {
                    if (cpuIssue) {
                        for (int i = 0; i < 20; i++) {
                            Util.sleepUninterruptable(1 * C.SEC);

                            Util.logAllThreadStackTraces();

                            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                            try {
                                ritual.logThreads();
                            } finally {
                                ritual.close();
                            }
                        }
                    }

                    SVClient.logSendDefectSync(false, message +
                            "\n" + C.END_OF_DEFECT_MESSAGE, null, verbose ?
                        FSIUtil.dumpStatForDefectLogging() : null);
                    UI.get().notify(MessageType.INFO, "Problem submitted. Thank you!");

                } catch (Exception e) {
                    Util.l(DlgDefect.class).warn("submit defect: " + Util.e(e));
                    UI.get().notify(MessageType.ERROR, "Failed to submit the " +
                                "problem " + UIUtil.e2msg(e) + ". Please try again.");
                    s_failedMessage = message;

                } finally {
                    UI.get().removeProgress(prog);
                }
            }
        };

        thd.setDaemon(true);
        thd.start();
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            submit(_txt.getText(), _btnSubmitData.getSelection());
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

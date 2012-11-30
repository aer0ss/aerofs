package com.aerofs.gui.sharing;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Util;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;

import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.FillLayout;

public class DlgJoinSharedFolder extends AeroFSDialog
{
    private Text _txtIC;
    private Button _btnJoin;

    private final String INVITATION_CODE_ERROR = "This invitation code was meant for a different " +
            "email address.\nPlease ask the person sharing with you to invite you using your " +
            Cfg.user() + " email address.";

    public DlgJoinSharedFolder(Shell parent)
    {
        super(parent, "Join a Shared Folder", false, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        GridLayout glShell = new GridLayout(2, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        glShell.verticalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(glShell);

        Label lblInvitationCode = new Label(shell, SWT.NONE);
        lblInvitationCode.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblInvitationCode.setText("Invitation code:");

        _txtIC = new Text(shell, SWT.BORDER);
        _txtIC.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                if (_btnJoin.isEnabled()) work();
            }
        });

        GridData gdText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gdText.widthHint = 160;
        _txtIC.setLayoutData(gdText);
        _txtIC.addVerifyListener(new VerifyListener() {
            @Override
            public void verifyText(VerifyEvent e)
            {
                verify(GUIUtil.getNewText(_txtIC, e));
            }
        });
        new Label(shell, SWT.NONE);

        Composite composite = new Composite(shell, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        FillLayout fl = new FillLayout(SWT.HORIZONTAL);
        fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(fl);

        _btnJoin = new Button(composite, SWT.NONE);
        _btnJoin.setText("Join");
        _btnJoin.setEnabled(false);
        _btnJoin.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                work();
            }
        });

        Button btnCancel = new Button(composite, SWT.NONE);
        btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
        btnCancel.setText(IDialogConstants.CANCEL_LABEL);
    }

    private void verify(String code)
    {
        if (code == null) code = _txtIC.getText();

        CodeType ct = InvitationCode.getType(code);
        boolean ready;

        switch (ct) {
        case TARGETED_SIGNUP:
            ready = false;
            UI.get().show(MessageType.WARN, INVITATION_CODE_ERROR);
            break;
        case SHARE_FOLDER:
            ready = true;
            break;
        default:
            ready = false;
            break;
        }
        _btnJoin.setEnabled(ready);
    }

    private void work()
    {
        final String ic = _txtIC.getText();

        Thread thd = new Thread() {
            @Override
            public void run()
            {
                Object prog = UI.get().addProgress("Joining the folder", true);
                try {
                    RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                    try {
                        ritual.joinSharedFolder(ic);
                    } finally {
                        ritual.close();
                    }

                    // TODO: use Ritual notifications to open shared folders when joined
                    //GUIUtil.launch(path.toAbsoluteString(Cfg.absRootAnchor()));

                } catch (Exception e) {
                    Util.l(this).warn("join store thru dlg: " + Util.e(e));
                    UI.get().notify(MessageType.ERROR, "Couldn't join the folder",
                            UIUtil.e2msgSentenceNoBracket(e), null);
                } finally {
                    UI.get().removeProgress(prog);
                }
            }
        };

        thd.setDaemon(true);
        thd.start();

        closeDialog();
    }
}

package com.aerofs.gui.netutil;

import com.aerofs.InternalDiagnostics;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.UIGlobals;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.slf4j.Logger;

public class DlgNetworkUtility extends AeroFSJFaceDialog
{
    private static final Logger l = Loggers.getLogger(DlgNetworkUtility.class);

    private final DID _did;
    private final String _alias;
    private final String _sname;
    private CompPing _ping;
    private Button _btnSubmit;

    /**
     * @param sname null to disable bandwidth test
     */
    public DlgNetworkUtility(Shell parentShell, DID did, String desc,
            String sname)
    {
        super("Network Utility", parentShell, false, false, false, true);
        _did = did;
        _alias = desc;
        _sname = sname;
    }

    public static boolean isAvailable(DID did)
    {
        return did != null && !did.equals(Cfg.did());
    }

    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayout gridLayout = (GridLayout) container.getLayout();
        gridLayout.marginHeight = GUIParam.MARGIN;
        gridLayout.marginWidth = GUIParam.MARGIN;

        Label label = new Label(container, SWT.NONE);
        label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
        label.setText("Remote Computer: " + _alias/* + " (" + _did.decimalString() + ")"*/);

        TabFolder tabFolder = new TabFolder(container, SWT.NONE);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        TabItem tbtmPing = new TabItem(tabFolder, SWT.NONE);
        tbtmPing.setText("Latency");

        _ping = new CompPing(tabFolder, SWT.NONE, _did);
        tbtmPing.setControl(_ping);

        if (_sname != null) {
            TabItem tbtmBandwidth = new TabItem(tabFolder, SWT.NONE);
            tbtmBandwidth.setText("Bandwidth");

            CompBandwidth compBandwidth = new CompBandwidth(tabFolder, _ping, SWT.NONE,
                    _did, _sname);
            tbtmBandwidth.setControl(compBandwidth);
        }

        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayout(new FillLayout(SWT.HORIZONTAL));
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

        _btnSubmit = new Button(composite, SWT.NONE);
        _btnSubmit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                submit();
            }
        });
        _btnSubmit.setText("    Submit Results    ");

        Button button = new Button(composite, SWT.NONE);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                DlgNetworkUtility.this.buttonPressed(IDialogConstants.CANCEL_ID);
            }
        });
        button.setText(IDialogConstants.CLOSE_LABEL);

        return container;
    }

    private void submit()
    {
        _ping.logStat();

        _btnSubmit.setEnabled(false);
        _btnSubmit.setText("Submitting...");

        GUI.get().safeWork(getShell(), new ISWTWorker() {
            @Override
            public void run() throws Exception
            {
                SVClient.logSendDefectSync(
                        false,
                        "network diagnosis results",
                        new Exception(),
                        InternalDiagnostics.dumpFullDaemonStatus(UIGlobals.ritual()),
                        false);
            }

            @Override
            public void okay()
            {
                _btnSubmit.setText("Done. Submit Again");
                _btnSubmit.setEnabled(true);
                getShell().layout();
            }

            @Override
            public void error(Exception e)
            {
                l.error("cannot send results: " + Util.e(e));
                _btnSubmit.setText("Failed. Try Again");
                _btnSubmit.setEnabled(true);
                getShell().layout();
            }
        });
    }

    // don't create button bar
    protected Control createButtonBar(Composite parent)
    {
        return null;
    }
}

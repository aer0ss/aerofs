package com.aerofs.gui.misc;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.controller.IViewNotifier.Type;
import com.aerofs.labeling.L;
import com.aerofs.ui.IUINotificationListener;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.update.Updater.Status;
import com.aerofs.ui.update.Updater.UpdaterNotification;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.S;
import com.swtdesigner.SWTResourceManager;

import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Composite;

import static com.aerofs.gui.GUIUtil.createLabel;

public class DlgAbout extends AeroFSDialog
{
    private CompSpin _compSpinUpdate;
    private Button _btnUpdate;
    private Label _lblUpdateStatus;

    public DlgAbout(Shell parent)
    {
        super(parent, "About", false, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        GridLayout glShell = new GridLayout(2, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        shell.setLayout(glShell);

        createLabel(shell, SWT.NONE);

        Label lblImage = createLabel(shell, SWT.NONE);
        lblImage.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false, 1, 3));
        lblImage.setImage(Images.get(Images.ICON_LOGO64));

        Label lblAerofs = createLabel(shell, SWT.NONE);
        lblAerofs.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, true, false, 1, 1));
        lblAerofs.setText(L.product());

        FontData fd = lblAerofs.getFont().getFontData()[0];
        lblAerofs.setFont(SWTResourceManager.getFont(fd.getName(), fd.getHeight() * 2, SWT.BOLD));

        Label lblVersion = createLabel(shell, SWT.NONE);
        lblVersion.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        lblVersion.setText("Version " + Cfg.ver());

        createLabel(shell, SWT.NONE);
        createLabel(shell, SWT.NONE);

        Label lblCopyright = createLabel(shell, SWT.NONE);
        lblCopyright.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        lblCopyright.setText("Copyright \u00a9 " + S.COPYRIGHT);

        Link link = new Link(shell, SWT.NONE);
        link.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        link.setText("Visit " + L.product() + " <a>Web site</a> and <a>Release Notes</a>.");
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                GUIUtil.launch(e.text.equals("Web site") ? WWW.MARKETING_HOST_URL :
                        "http://support.aerofs.com/entries/23864878");
            }
        });

        createLabel(shell, SWT.NONE);
        createLabel(shell, SWT.NONE);

        Composite composite = new Composite(shell, SWT.NONE);
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.marginHeight = 0;
        glComposite.marginWidth = 0;
        composite.setLayout(glComposite);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

        _btnUpdate = GUIUtil.createButton(composite, SWT.NONE);
        _compSpinUpdate = new CompSpin(composite, SWT.NONE);
        _lblUpdateStatus = createLabel(composite, SWT.NONE);
        _lblUpdateStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        shell.addShellListener(new ShellAdapter() {
            @Override
            public void shellActivated(ShellEvent e)
            {
                _btnUpdate.setFocus();
            }

            @Override
            public void shellClosed(ShellEvent e)
            {
                UIGlobals.notifier().removeListener(Type.UPDATE, _updateListener);
            }
        });

        UIGlobals.notifier().addListener(Type.UPDATE, _updateListener);
        setUpdateStatus(UIGlobals.updater().getUpdateStatus(), 0);
    }

    private SelectionAdapter _btnUpdateSelectionAdapter =
        new SelectionAdapter(){};

    private void setUpdateButton(String text, SelectionAdapter sa,
            boolean enabled, boolean warningSign)
    {
        _btnUpdate.removeSelectionListener(_btnUpdateSelectionAdapter);

        if (null != sa) {
            _btnUpdateSelectionAdapter = sa;
            _btnUpdate.addSelectionListener(_btnUpdateSelectionAdapter);
        }
        _btnUpdate.setText(text);
        _btnUpdate.setEnabled(enabled);
        _btnUpdate.setImage(warningSign ? Images.get(Images.ICON_WARNING) : null);
        _btnUpdate.setSize(_btnUpdate.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        getShell().pack();
    }

    private final IUINotificationListener _updateListener = new IUINotificationListener() {
        @Override
        public void onNotificationReceived(Object notification)
        {
            UpdaterNotification n = (UpdaterNotification)notification;
            setUpdateStatus(n.status, n.progress);
        }
    };

    private void setUpdateStatus(Status us, int progress)
    {
        if (getShell().isDisposed()) return;

        String status = "";

        boolean visible;
        switch (us) {
        case NONE:
            visible = false;
            status = "";
            _compSpinUpdate.stop();
            setUpdateButton(S.BTN_CHECK_UPDATE, new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIGlobals.updater().checkForUpdate(true);
                }
            }, true, false);
            break;
        case ONGOING:
            visible = true;
            String btnStatus = "";
            if (progress > 0) {
                status = S.LBL_UPDATE_ONGOING + " " + progress + "%";
                btnStatus = S.BTN_APPLY_UPDATE;
            } else {
                status = S.LBL_UPDATE_CHECKING;
                btnStatus = S.BTN_CHECK_UPDATE;
            }
            _compSpinUpdate.start();
            setUpdateButton(btnStatus, null, false, false);
            break;
        case LATEST:
            visible = true;
            status = S.LBL_UPDATE_LATEST;
            _compSpinUpdate.stop(Images.get(Images.ICON_TICK));
            setUpdateButton(S.BTN_CHECK_UPDATE, new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIGlobals.updater().checkForUpdate(true);
                }
            }, true, false);
            break;
        case APPLY:
            visible = true;
            status = S.LBL_UPDATE_APPLY;
            _compSpinUpdate.stop();
            setUpdateButton(S.BTN_APPLY_UPDATE, new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIGlobals.updater().execUpdateFromMenu();
                }
            }, true, true);
            break;
        case ERROR:
            visible = true;
            status = S.LBL_UPDATE_ERROR;
            _compSpinUpdate.error();
            setUpdateButton(S.BTN_CHECK_UPDATE, new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIGlobals.updater().checkForUpdate(true);
                }
            }, true, false);
            break;
        default:
            assert false;
            return;
        }

        if (visible) {
            _lblUpdateStatus.setText(status);
        }
    }
}

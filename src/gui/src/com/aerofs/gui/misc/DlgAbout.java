package com.aerofs.gui.misc;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.controller.IViewNotifier.Type;
import com.aerofs.gui.*;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUINotificationListener;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.update.Updater.Status;
import com.aerofs.ui.update.Updater.UpdaterNotification;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

import static com.aerofs.gui.GUIUtil.*;
import static com.google.common.base.Preconditions.checkState;

public class DlgAbout extends AeroFSDialog
{
    private static final Logger l = Loggers.getLogger(DlgAbout.class);

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
        Composite compProduct = new Composite(shell, SWT.NONE);

        Label lblImage = createLabel(compProduct, SWT.NONE);
        lblImage.setImage(Images.get(Images.ICON_LOGO64));

        Label lblProduct = createLabel(compProduct, SWT.BOLD);
        lblProduct.setFont(makeEmphasis(lblProduct.getFont()));
        lblProduct.setText(L.product());

        Label lblVersion = createLabel(compProduct, SWT.NONE);
        lblVersion.setFont(makeSubtitle(lblVersion.getFont()));
        lblVersion.setText("Version " + Cfg.ver());

        Composite compContent = new Composite(shell, SWT.NONE);

        Composite compLinks = new Composite(compContent, SWT.NONE);

        Link linkWebsite = new Link(compLinks, SWT.NONE);
        // N.B. HACK ALERT(AT): the extra white spaces at the start and end is necessary to make this the widest
        //   widget possible on the screen when rendered. We use this to control layout and to ensure the total
        //   width doesn't change when lblUpdateStatus's content changes.
        linkWebsite.setText("    Visit " + L.brand() + " <a>Web site</a> and <a>Release Notes</a>.    ");

        Link linkOss = new Link(compLinks, SWT.NONE);
        linkOss.setText(L.brand() + " uses <a>free software</a>.");

        Composite compUpdate = new Composite(compContent, SWT.NONE);
        _btnUpdate = createButton(newPackedButtonContainer(compUpdate), SWT.PUSH);
        Composite compStatus = new Composite(compUpdate, SWT.NONE);
        _compSpinUpdate = new CompSpin(compStatus, SWT.NONE);
        _lblUpdateStatus = createLabel(compStatus, SWT.NONE);

        Composite compCopyright = new Composite(shell, SWT.NONE);

        Label lblCopyright = createLabel(compCopyright, SWT.NONE);
        lblCopyright.setFont(makeSubtitle(lblCopyright.getFont()));
        lblCopyright.setText("Copyright \u00a9 " + S.BASE_COPYRIGHT);

        Label lblAllRights = createLabel(compCopyright, SWT.NONE);
        lblAllRights.setFont(makeSubtitle(lblAllRights.getFont()));
        lblAllRights.setText(S.ALL_RIGHTS_RESERVED);

        GridLayout shellLayout = newGridLayout();
        shellLayout.marginWidth = GUIParam.WIDE_MARGIN;
        shellLayout.marginHeight = GUIParam.MARGIN;
        shellLayout.verticalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(shellLayout);

        compProduct.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        compContent.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, true, true));
        compCopyright.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, true, false));

        compProduct.setLayout(newCentredRowLayout(SWT.VERTICAL));

        RowLayout contentLayout = newCentredRowLayout(SWT.VERTICAL);
        contentLayout.marginHeight = GUIParam.VERTICAL_SPACING;
        contentLayout.spacing = GUIParam.MAJOR_SPACING;
        compContent.setLayout(contentLayout);

        RowLayout linksLayout = newCentredRowLayout(SWT.VERTICAL);
        linksLayout.spacing = 4;
        compLinks.setLayout(linksLayout);

        compUpdate.setLayout(newCentredRowLayout(SWT.VERTICAL));
        compStatus.setLayout(newCentredRowLayout(SWT.HORIZONTAL));
        compCopyright.setLayout(newCentredRowLayout(SWT.VERTICAL));

        SelectionListener onLinkClicked = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                GUIUtil.launch(getLink(e));
            }

            private String getLink(SelectionEvent e) {
                switch (e.text) {
                    case "Web site":
                        return WWW.MARKETING_HOST_URL;
                    case "Release Notes":
                        return "https://support.aerofs.com/entries/23864878";
                    case "free software":
                        return "https://support.aerofs.com/hc/en-us/articles/202866484";
                    default:
                        l.error("Invalid link: {}", e.text);
                        checkState(false); // illegal state, this indicates a programming error
                        return "";
                }
            }
        };

        linkWebsite.addSelectionListener(onLinkClicked);
        linkOss.addSelectionListener(onLinkClicked);

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
    }

    private final IUINotificationListener _updateListener = notification -> {
        UpdaterNotification n = (UpdaterNotification)notification;
        setUpdateStatus(n.status, n.progress);
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
            getShell().layout(new Control[] { _lblUpdateStatus });
        }
    }
}

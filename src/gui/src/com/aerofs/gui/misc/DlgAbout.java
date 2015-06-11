package com.aerofs.gui.misc;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.controller.IViewNotifier.Type;
import com.aerofs.gui.*;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Versions;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUINotificationListener;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.update.Updater.Status;
import com.aerofs.ui.update.Updater.UpdaterNotification;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

import static com.aerofs.gui.GUIUtil.*;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

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
        linkWebsite.setText("Visit the " + L.brand() + " <a>web site</a> or view the " + L.brand() +
                " <a>release notes</a>.");

        Composite compUpdate = new Composite(compContent, SWT.NONE);
        _btnUpdate = createButton(newPackedButtonContainer(compUpdate), SWT.PUSH);
        Composite compStatus = new Composite(compUpdate, SWT.NONE);
        _compSpinUpdate = new CompSpin(compStatus, SWT.NONE);
        _lblUpdateStatus = createLabel(compStatus, SWT.WRAP);

        Composite compCopyright = new Composite(shell, SWT.NONE);

        Label lblCopyright = createLabel(compCopyright, SWT.NONE);
        lblCopyright.setFont(makeSubtitle(lblCopyright.getFont()));
        lblCopyright.setText("Copyright \u00a9 " + S.BASE_COPYRIGHT + " " + S.ALL_RIGHTS_RESERVED);

        Link linkOss = new Link(compCopyright, SWT.NONE);
        linkOss.setFont(makeSubtitle(linkOss.getFont()));
        linkOss.setText("This product uses third-party <a>free software</a>.");

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
                    case "web site":
                        return WWW.MARKETING_HOST_URL;
                    case "release notes":
                        return "https://support.aerofs.com/hc/en-us/articles/201439644";
                    case "free software":
                        return "https://www.aerofs.com/terms/#freesoftware";
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

        switch (us) {
        case NONE:
            _lblUpdateStatus.setText("");
            _compSpinUpdate.stop();
            setUpdateButton(S.BTN_CHECK_UPDATE, new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    UIGlobals.updater().checkForUpdate(true);
                }
            }, true, false);
            break;
        case ONGOING:
            if (progress > 0) {
                _lblUpdateStatus.setText(S.LBL_UPDATE_ONGOING + " " + progress + "%");
                setUpdateButton(S.BTN_APPLY_UPDATE, null, false, false);
            } else {
                _lblUpdateStatus.setText(S.LBL_UPDATE_CHECKING);
                setUpdateButton(S.BTN_CHECK_UPDATE, null, false, false);
            }
            _compSpinUpdate.start();
            break;
        case LATEST:
            _lblUpdateStatus.setText(S.LBL_UPDATE_LATEST);
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
            _lblUpdateStatus.setText(S.LBL_UPDATE_APPLY);
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
            _lblUpdateStatus.setText(S.LBL_UPDATE_ERROR);
            _compSpinUpdate.error();
            setUpdateButton(S.BTN_CHECK_UPDATE, new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIGlobals.updater().checkForUpdate(true);
                }
            }, true, false);
            break;
        case DISABLED:
            RowData layoutData = new RowData();
            layoutData.exclude = true;
            // N.B. hide _btnUpdate's parent because _btnUpdate is a child of a packed container with negative margin.
            _btnUpdate.getParent().setLayoutData(layoutData);
            _btnUpdate.getParent().setVisible(false);

            setUpdateStatus(Status.ONGOING, 0);

            ListeningExecutorService executor = listeningDecorator(newSingleThreadExecutor());
            // returns true iff the client is up-to-date
            Callable<Boolean> task = () -> Versions.compare(Cfg.ver(), UIGlobals.updater().getServerVersion())
                    == Versions.CompareResult.NO_CHANGE;
            FutureCallback<Boolean> callback = new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    if (result) {
                        setUpdateStatus(Status.LATEST, 0);
                    } else {
                        _lblUpdateStatus.setText(S.LBL_UPDATE_OUT_OF_DATE);
                        _compSpinUpdate.warning();
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    setUpdateStatus(Status.ERROR, 0);
                }
            };

            addCallback(executor.submit(task), callback, new GUIExecutor(getShell()));

            break;
        default:
            assert false;
        }

        getShell().layout(new Control[] { _lblUpdateStatus, _btnUpdate });
        getShell().pack();
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.InstallActor;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.CredentialActor;
import com.aerofs.controller.SignInActor.OpenIdGUIActor;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.lib.LibParam;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

/**
 * N.B. openDialog should return a non-null result if the setup succeeded,
 *   and it should return null if the user cancels the setup.
 */
public class DlgMultiuserSetup extends AeroFSDialog
{
    private SetupModel _model;

    private AbstractSetupPage _page;

    public DlgMultiuserSetup(Shell shell, SetupModel model)
    {
        super(shell, null, false, true);

        _model = model
                .setSignInActor(LibParam.OpenId.enabled() ?
                        new OpenIdGUIActor() : new CredentialActor())
                .setInstallActor(new InstallActor.MultiUser());
    }

    @Override
    protected void open(Shell shell)
    {
        shell.setLayout(new FillLayout());

        // show the starting page
        loadPage(createLoginPage());
    }

    @Override
    protected void setShellSize()
    {
        getShell().setSize(540, 420);
    }

    public void loadPage(AbstractSetupPage page)
    {
        /**
         * dispose the previous page and loads the next
         *
         * since the meaningful states are stored in SetupModel, the only reason to keep
         *   old pages around would be to optimize for the case of users going back
         *   to previous pages.
         */
        if (_page != null) _page.dispose();
        _page = page;
        _page.setModel(_model);
        _page.initPage();

        getShell().layout();
    }

    private AbstractSetupPage createLoginPage()
    {
        AbstractSetupPage page = (LibParam.OpenId.enabled() ?
                                  new PageOpenIdSignIn(getShell()) : new PageCredentialSignIn(getShell()));
        page.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent event)
            {
                switch (event.detail) {
                case SWT.TRAVERSE_PAGE_NEXT:
                    if (_model.getNeedSecondFactor()) {
                        loadPage(createTwoFactorPage());
                    } else {
                        loadPage(createSelectStoragePage());
                    }
                    break;
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    closeDialog();
                    break;
                }
            }
        });
        return page;
    }

    private AbstractSetupPage createTwoFactorPage()
    {
        AbstractSetupPage page = new PageSecondFactor(getShell());
        page.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent event)
            {
                switch (event.detail) {
                case SWT.TRAVERSE_PAGE_NEXT:
                    loadPage(createSelectStoragePage());
                    break;
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    loadPage(createLoginPage());
                    break;
                }
            }
        });
        return page;
    }

    private AbstractSetupPage createSelectStoragePage()
    {
        PageSelectStorage page = new PageSelectStorage(getShell());
        page.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent event)
            {
                switch (event.detail) {
                case SWT.TRAVERSE_PAGE_NEXT:
                    loadPage(_model._isLocal ? createLocalStoragePage() : createS3StoragePage());
                    break;
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    loadPage(createLoginPage());
                    break;
                }
            }
        });
        return page;
    }

    private AbstractSetupPage createLocalStoragePage()
    {
        PageLocalStorage page = new PageLocalStorage(getShell());
        page.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent event)
            {
                switch (event.detail) {
                case SWT.TRAVERSE_PAGE_NEXT:
                    closeDialog(_model);
                    break;
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    loadPage(createSelectStoragePage());
                    break;
                }
            }
        });
        return page;
    }

    private AbstractSetupPage createS3StoragePage()
    {
        PageS3Storage page = new PageS3Storage(getShell());
        page.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent event)
            {
                switch (event.detail) {
                case SWT.TRAVERSE_PAGE_NEXT:
                    closeDialog(_model);
                    break;
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    loadPage(createSelectStoragePage());
                    break;
                }
            }
        });
        return page;
    }
}

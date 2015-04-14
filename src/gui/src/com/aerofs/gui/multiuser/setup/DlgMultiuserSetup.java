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
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Shell;

import java.util.Map;

import static com.google.common.collect.Maps.newEnumMap;

/**
 * N.B. openDialog should return a non-null result if the setup succeeded,
 *   and it should return null if the user cancels the setup.
 */
public class DlgMultiuserSetup extends AeroFSDialog
{
    enum PageID {
        PAGE_LOGIN,
        PAGE_TWO_FACTOR,
        PAGE_SELECT_STORAGE,
        PAGE_LOCAL_STORAGE,
        PAGE_S3_STORAGE,
        PAGE_SWIFT_STORAGE,
        PAGE_PASSPHRASE,
    }

    private SetupModel _model;

    private StackLayout _layout;
    private Map<PageID, AbstractSetupPage> _pages;

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
        _layout = new StackLayout();
        shell.setLayout(_layout);

        _pages = newEnumMap(PageID.class);

        _pages.put(PageID.PAGE_LOGIN, LibParam.OpenId.enabled() ? new PageOpenIdSignIn(shell)
                : new PageCredentialSignIn(shell));
        _pages.put(PageID.PAGE_TWO_FACTOR, new PageSecondFactor(shell));
        _pages.put(PageID.PAGE_SELECT_STORAGE, new PageSelectStorage(shell));
        _pages.put(PageID.PAGE_LOCAL_STORAGE, new PageLocalStorage(shell));
        _pages.put(PageID.PAGE_S3_STORAGE, new PageS3Storage(shell));
        _pages.put(PageID.PAGE_SWIFT_STORAGE, new PageSwiftStorage(shell));
        _pages.put(PageID.PAGE_PASSPHRASE, new PagePassphrase(shell));

        for (AbstractSetupPage page : _pages.values()) {
            page.setDialog(this);
            page.setModel(_model);
            page.initPage();
        }

        loadPage(PageID.PAGE_LOGIN);
    }

    public void loadPage(PageID pageID)
    {
        AbstractSetupPage page = _pages.get(pageID);

        page.readFromModel(_model);
        _layout.topControl = page;

        getShell().layout(true);
    }
}

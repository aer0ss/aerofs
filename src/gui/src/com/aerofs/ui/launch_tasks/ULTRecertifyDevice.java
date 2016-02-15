/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.cli.CLI;
import com.aerofs.controller.CredentialUtil;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.CredentialActor;
import com.aerofs.gui.GUI;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.aerofs.lib.cfg.ICfgStore.CONTACT_EMAIL;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class ULTRecertifyDevice extends UILaunchTask
{
    private static final Logger l = Loggers.getLogger(ULTRecertifyDevice.class);
    private static final int REFRESH_MARGIN_DAYS = 150;
    private UserID _userId;
    private DID _deviceId;

    @Inject
    ULTRecertifyDevice(IScheduler sched, UserID userId, DID deviceId)
    {
        super(sched);
        _userId = userId;
        _deviceId = deviceId;
    }

    @Override
    protected void run_() throws Exception
    {
        // First, if the existing cert signing path is invalid (we upgraded the CA or it has
        // expired?) then ask the user for authentication info so we can recertify.
        // If the signing path exists, check if about half of the life of the cert is left; within
        // let's REFRESH_MARGIN_DAYS, we will use the existing certificate to sign in this device
        // and request a new cert.
        // Or, maybe everything is okay. In which case, do nothing.
        if (!BaseSecUtil.signingPathExists(Cfg.cert(), Cfg.cacert())) {

            recertify(this::signInToRecertify);

        } else if (Cfg.recertify(Cfg.absRTRoot())
                || !BaseSecUtil.validForAtLeast(Cfg.cert(), REFRESH_MARGIN_DAYS * C.DAY)
                || !BaseSecUtil.hasSufficientExtKeyUsage(Cfg.cert())) {
            recertify(() -> {
                l.info("Attempting to refresh the device certificate...");

                SPBlockingClient mutualAuthClient = newMutualAuthClientFactory().create();
                mutualAuthClient.signInDevice(_userId.getString(), BaseUtil.toPB(_deviceId));
                CredentialUtil.recertifyDevice(_userId, mutualAuthClient);

                l.info("Successfully refreshed the device certificate.");
            });
        } else {
            l.debug("client cert is signed by CA, good");
        }
    }

    static interface NullCallable { void call() throws Exception; }

    // Call the recertify action. Regardless of its result, restart the dm afterward.
    // Just let any exception from here bubble up to the caller, recall we are in a retryable task
    private void recertify(NullCallable recertifyAction) throws Exception
    {
        UIGlobals.dm().stopIgnoreException();
        try {
            recertifyAction.call();
            Cfg.init_(Cfg.absRTRoot(), false);
        } finally {
            UIGlobals.dm().start();
        }
    }

    static class ExceptionWrapper { @Nullable Exception _ex; }

    static class ExInputAborted extends Exception {
        private static final long serialVersionUID = 0;
    }

    /**
     * Handle the UI interaction to get user signin; that user signin is then leveraged
     * to recertify this device.
     *
     * Note that this is only used if the cert has no signing path, normally the refresh
     * path above is used.
     */
    private void signInToRecertify() throws Exception
    {
        l.info("attempting to recertify device {}", Cfg.did());

        final ExceptionWrapper ac = new ExceptionWrapper();
        final SetupModel setupModel = new SetupModel(Cfg.absRTRoot());

        UI.get().exec(new Runnable()
        {
            @Override
            public void run()
            {
                // prepopulate the setupModel with a user name; in the Team Server case it
                // may be modified by the user.
                setupModel.setUserID(
                        L.isMultiuser() ? Cfg.db().get(CONTACT_EMAIL) : Cfg.user().getString());

                if (UI.isGUI()) authenticateGUI();
                else typeCredentialsInCLI();
            }

            private void typeCredentialsInCLI()
            {
                CLI.get().show(MessageType.INFO, S.SIGN_IN_TO_RECERTIFY_ACTION);
                CLI.get().show(MessageType.INFO, "To learn more about why this is needed, see: "
                        + WWW.RECERTIFY_SUPPORT_URL);
                try {
                    setupModel.setSignInActor(new CredentialActor());

                    // Team server recertify can use any admin account.
                    // Non-team server can't change the email address, so don't bother asking.
                    if (L.isMultiuser()) {
                        setupModel.setUserID(CLI.get().askText(
                                S.ADMIN_EMAIL, setupModel.getUsername()));
                    } else {
                        CLI.get().show(MessageType.INFO, "[ Sign in for user name "
                                + Cfg.user() + " ]");
                    }
                    setupModel.setPassword(new String(CLI.get().askPasswd(
                            L.isMultiuser() ? S.ADMIN_PASSWD : S.SETUP_PASSWD)));
                } catch (Exception e) {
                    ac._ex = e;
                }
            }

            private void authenticateGUI() {
                DlgSignInToRecertify dlg = new DlgSignInToRecertify(GUI.get().sh(), setupModel);
                dlg.open();
                if (dlg.isCancelled()) {
                    ac._ex = new ExInputAborted();
                }
            }
        });

        // Any exception from the UI thread, including user 'cancel' action,
        // is thrown up to the framework; it will call us again later via exponential retries.
        if (ac._ex != null) throw ac._ex;

        setupModel.doSignIn();
        SPBlockingClient sp = setupModel.getClient();

        if (L.isMultiuser()) CredentialUtil.recertifyTeamServerDevice(Cfg.user(), sp);
        else CredentialUtil.recertifyDevice(Cfg.user(), sp);

        l.info("successfully recertified device");

        // For some reason the old certificate is cached somewhere in memory. It causes logging in
        // to SP to fail and in turn repetitive UI messages complaining about wrong certificate.
        // Although it's ideal to identify and fix the root cause, it's probably not worth the
        // efforts since so far most old Team Servers should have been recertified. As a quick
        // workaround, we simply shutdown the process and let the user to restart it manually.
        // (User instructions are in S.TYPE_ADMIN_PASSWORD_TO_RECERTIFY_TEAM_SERVER.)
        UI.get().shutdown();
    }
}

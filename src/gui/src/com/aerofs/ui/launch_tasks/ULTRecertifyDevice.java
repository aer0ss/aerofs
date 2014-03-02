/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.cli.CLI;
import com.aerofs.controller.CredentialUtil;
import com.aerofs.gui.GUI;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newOneWayAuthClientFactory;

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
        if (!SecUtil.signingPathExists(Cfg.cert(), Cfg.cacert())) {
            recertify(new NullCallable() {
                // FIXME: this needs an update to a new UI element, this one is obsolete
                @Override
                public void call() throws Exception {
                    if (L.isMultiuser()) {
                        recertifyTeamServer();
                    } else {
                        recertifyClient();
                    }
                }
            });
        } else if (Cfg.recertify(Cfg.absRTRoot())
                || (!SecUtil.validForAtLeast(Cfg.cert(), REFRESH_MARGIN_DAYS * C.DAY))) {
            recertify(new NullCallable() {
                @Override
                public void call() throws Exception {
                    l.info("Attempting to refresh the device certificate...");

                    SPBlockingClient mutualAuthClient = newMutualAuthClientFactory().create();
                    mutualAuthClient.signInDevice(_userId.getString(), _deviceId.toPB());
                    CredentialUtil.recertifyDevice(_userId, mutualAuthClient);

                    l.info("Successfully refreshed the device certificate.");
                }
            });
        } else {
            l.debug("client cert is signed by CA, good");
        }
    }

    static interface NullCallable { void call() throws Exception; }

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

    // Used to pass data between the current and UI threads.
    static class AdminCredentials {
        @Nullable UserID _userID;       // Nonnull if _ex is null
        @Nullable char[] _password;     // Nonnull if _ex is null
        @Nullable Exception _ex;        // Nonnull if error happened in the UI thread.
    }

    static class ExInputAborted extends Exception {
        private static final long serialVersionUID = 0;
    }

    private void recertifyTeamServer()
            throws Exception
    {
        l.info("attempting to recertify Team Server {}", Cfg.did());

        final AdminCredentials ac = new AdminCredentials();

        UI.get().exec(new Runnable()
        {
            @Override
            public void run()
            {
                String email = Cfg.db().get(Key.CONTACT_EMAIL);
                if (UI.isGUI()) typeCredentialsInGUI(email);
                else typeCredentialsInCLI(email);
            }

            private void typeCredentialsInCLI(String email)
            {
                CLI.get().show(MessageType.INFO, S.TYPE_ADMIN_PASSWORD_TO_RECERTIFY_TEAM_SERVER);
                try {
                    ac._userID = UserID.fromExternal(CLI.get().askText(S.ADMIN_EMAIL, email));
                    ac._password = CLI.get().askPasswd(S.ADMIN_PASSWD);
                } catch (Exception e) {
                    ac._ex = e;
                }
            }

            private void typeCredentialsInGUI(String email) {
                DlgTypeAdminCredential dlg = new DlgTypeAdminCredential(GUI.get().sh(), email);
                dlg.open();
                if (dlg.isCancelled()) {
                    ac._ex = new ExInputAborted();
                } else {
                    ac._userID = dlg.getUserID();
                    ac._password = dlg.getPasswd();
                }
            }
        });

        // Throw it up so the framework will call us again later via exponential retries.
        if (ac._ex != null) throw ac._ex;
        // This is guaranteed by the above code.
        assert ac._userID != null;
        assert ac._password != null;

        SPBlockingClient sp = newOneWayAuthClientFactory().create();
        sp.credentialSignIn(ac._userID.getString(),
                ByteString.copyFrom(new String(ac._password).getBytes()));
        CredentialUtil.recertifyTeamServerDevice(Cfg.user(), sp);
        l.info("successfully recertified Team Server");

        // For some reason the old certificate is cached somewhere in memory. It causes logging in
        // to SP to fail and in turn repeatitive UI messages complaining about wrong certificate.
        // Although it's ideal to identify and fix the root cause, it's probably not worth the
        // efforts since so far most old Team Servers should have been recertified. As a quick
        // workaround, we simply shutdown the process and let the user to restart it manually.
        // (User instructions are in S.TYPE_ADMIN_PASSWORD_TO_RECERTIFY_TEAM_SERVER.)
        UI.get().shutdown();
    }

    // FIXME : this function depends on the sha'ed password being stored locally.
    // This will be an unsafe assumption very soon. Should prompt the user to re-authenticate
    // their identity - either using an AeroFS password or using an identity system like OpenID.
    private void recertifyClient() throws Exception
    {
        l.info("attempting to recertify client {}", Cfg.did());
        // We have a cert that is not trusted by the current CA.  Try to get a new one.
        SPBlockingClient sp = newOneWayAuthClientFactory().create();
        sp.signInUser(Cfg.user().getString(), Cfg.scryptedPB());

        CredentialUtil.recertifyDevice(Cfg.user(), sp);
        l.info("successfully recertified client");
    }
}

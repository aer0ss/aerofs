/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.base.Loggers;
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

public class ULTRecertifyDevice extends UILaunchTask
{
    private final SPBlockingClient.Factory _spfact;
    private static final Logger l = Loggers.getLogger(ULTRecertifyDevice.class);

    @Inject
    ULTRecertifyDevice(IScheduler sched)
    {
        super(sched);
        _spfact = new SPBlockingClient.Factory();
    }

    @Override
    protected void run_()
            throws Exception
    {
        // Determine if the certificate is signed by current CA or needs to be reissued.
        // Check if RTROOT/cert is signed by APPROOT/cacert.pem
        // If so, we're done.
        if (SecUtil.signingPathExists(Cfg.cert(), Cfg.cacert())) {
            l.debug("client cert is signed by CA, good");
            return;
        }

        UIGlobals.dm().stopIgnoreException();
        try {
            if (L.isMultiuser()) {
                recertifyTeamServer();
            } else {
                recertifyClient();
            }
            // Reload the cert in memory
            Cfg.init_(Cfg.absRTRoot(), false);
        } finally {
            // restart the daemon regardless of whether we succeed.
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
                String email = Cfg.db().get(Key.MULTIUSER_CONTACT_EMAIL);
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
        byte[] scrypted = SecUtil.scrypt(ac._password, ac._userID);

        SPBlockingClient sp = _spfact.create_(ac._userID,
                SPBlockingClient.ONE_WAY_AUTH_CONNECTION_CONFIGURATOR);
        sp.signIn(ac._userID.getString(), ByteString.copyFrom(scrypted));
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

    private void recertifyClient()
            throws Exception
    {
        l.info("attempting to recertify client {}", Cfg.did());
        // We have a cert that is not trusted by the current CA.  Try to get a new one.
        SPBlockingClient sp = _spfact.create_(Cfg.user(),
                SPBlockingClient.ONE_WAY_AUTH_CONNECTION_CONFIGURATOR);
        sp.signInRemote();
        CredentialUtil.recertifyDevice(Cfg.user(), sp);
        l.info("successfully recertified client");
    }
}

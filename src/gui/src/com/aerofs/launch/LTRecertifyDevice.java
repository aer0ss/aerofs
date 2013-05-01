/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.launch;

import com.aerofs.base.Loggers;
import com.aerofs.controller.CredentialUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class LTRecertifyDevice extends LaunchTask
{
    private final SPBlockingClient.Factory _spfact;
    private static final Logger l = Loggers.getLogger(LTRecertifyDevice.class);

    @Inject
    LTRecertifyDevice(IScheduler sched)
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
        UI.dm().stop();
        if (L.isMultiuser()) {
            recertifyTeamServer();
        } else {
            recertifyClient();
        }
        UI.dm().start();
    }

    private void recertifyTeamServer()
            throws Exception
    {
        l.info("attempting to recertify Team Server {}", Cfg.did());
        // TODO WW - spawn a dialog and wait for user to enter credentials, then use credentials
        // below
        // UserID user;
        // char[] passwd;
        // <spawn dialog, populate the two fields>
        // byte[] scrypted = SecUtil.scrypt(passwd, user);
        // SPBlockingClient sp = _spfact.create_(
        //        SPBlockingClient.ONE_WAY_AUTH_CONNECTION_CONFIGURATOR);
        //CredentialUtil.recertifyTeamServerDevice(Cfg.user(), sp);
        l.info("successfully recertified Team Server", Cfg.did());
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

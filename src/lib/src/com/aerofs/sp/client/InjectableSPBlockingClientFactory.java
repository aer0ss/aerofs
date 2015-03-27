/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.client;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.inject.Inject;

/**
 * Helper class to simplify creation of SP client factories in the desktop client
 *
 * This is needed because we're using Cfg* classes which only make sense in the desktop client
 * and we don't want to contaminate other modules with that.
 */
public class InjectableSPBlockingClientFactory extends SPBlockingClient.Factory
{

    @Inject
    public InjectableSPBlockingClientFactory(CfgLocalUser user, CfgLocalDID did,
            CfgKeyManagersProvider key, CfgCACertificateProvider cacert)
    {
        super(SP.URL, user.get(), did.get(), key, cacert);
    }

    public static InjectableSPBlockingClientFactory newMutualAuthClientFactory()
    {
        return new InjectableSPBlockingClientFactory(new CfgLocalUser(),
                new CfgLocalDID(), new CfgKeyManagersProvider(), new CfgCACertificateProvider());
    }

    public static InjectableSPBlockingClientFactory newOneWayAuthClientFactory()
    {
        return new InjectableSPBlockingClientFactory(new CfgLocalUser(),
                new CfgLocalDID(), null, new CfgCACertificateProvider());
    }
}

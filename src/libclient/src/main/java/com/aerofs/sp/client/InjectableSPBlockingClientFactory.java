/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.client;

import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.inject.Inject;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

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
        super(getUrlFromConfiguration(), user.get(), did.get(), key, cacert);
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

    private static String getUrlFromConfiguration()
    {
        return getStringProperty("base.sp.url", "https://sp.aerofs.com/sp/");
    }
}

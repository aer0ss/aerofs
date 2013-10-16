/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.analytics;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.IAnalyticsPlatformProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPBlockingClient.Factory;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * This is the desktop client implementation of our analytics properties
 * See also com.aerofs.android.service.AndroidAnalyticsProperties
 */
public class DesktopAnalyticsProperties implements IAnalyticsPlatformProperties
{
    private static final Logger l = Loggers.getLogger(DesktopAnalyticsProperties.class);

    @Override
    public @Nullable UserID getUserID()
    {
        return Cfg.user();
    }

    @Override
    public @Nullable DID getDid()
    {
        return Cfg.did();
    }

    @Override
    public @Nullable String getVersion()
    {
        return Cfg.ver();
    }

    @Override
    public @Nullable String getOSFamily()
    {
        return OSUtil.get().getOSFamily().getString();
    }

    @Override
    public @Nullable String getOSName()
    {
        return OSUtil.get().getFullOSName();
    }

    @Override
    public long getSignupDate()
    {
        return Cfg.db().getLong(Key.SIGNUP_DATE);
    }

    private long _lastSPOrgIDCheck;
    private String _orgID;

    @Override
    public String getOrgID()
            throws Exception
    {
        if (_orgID == null || ((System.currentTimeMillis() - _lastSPOrgIDCheck) >= 1 * C.DAY))
        {
            SPBlockingClient sp = new Factory().create_(Cfg.user());
            sp.signInRemote();

            _orgID = sp.getOrganizationID().getOrgId();
            _lastSPOrgIDCheck = System.currentTimeMillis();
            l.info("orgID: {}", _orgID);
        }

        return _orgID;

    }
}

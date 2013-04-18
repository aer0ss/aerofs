/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.analytics;

import com.aerofs.base.analytics.IAnalyticsPlatformProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.OSUtil;

import javax.annotation.Nullable;

/**
 * This is the desktop client implementation of our analytics properties
 * See also com.aerofs.android.service.AndroidAnalyticsProperties
 */
public class DesktopAnalyticsProperties implements IAnalyticsPlatformProperties
{
    @Override
    public @Nullable UserID getUser()
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
}

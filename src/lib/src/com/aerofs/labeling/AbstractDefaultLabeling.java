/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling;

import com.aerofs.lib.cfg.Cfg;

/**
 * The default labeling class for concrete labeling classes to inherit. The default labeling
 * represents an AeroFS single-user client.
 */
public abstract class AbstractDefaultLabeling implements ILabeling
{
    // Note that for now we have chosen to only use one map file, so the mapping for classes that
    // inherit from this class might be broken. This isn't a big deal though, since you can identify
    // what function is being called using line numbers.

    @Override
    public int trayIconAnimationFrameCount()
    {
        return 14;
    }

    @Override
    public long trayIconAnimationFrameInterval()
    {
        return 80;
    }

    @Override
    public String vendor()
    {
        return "Air Computing Inc.";
    }

    @Override
    public String webHost()
    {
        return "www.aerofs.com";
    }

    @Override
    public String xmppServerAddr()
    {
        return Cfg.staging() ? "staging.aerofs.com" : "x.aerofs.com";
    }

    @Override
    public int xmppServerPort()
    {
        return Cfg.staging() ? 9328 : 443;
    }

    @Override
    public String jingleRelayHost()
    {
        return "97.107.139.17";
    }

    @Override
    public int jingleRelayPort()
    {
        return Cfg.staging() ? 7583 : 80;
    }

    @Override
    public String spUrl()
    {
        return "https://" + (Cfg.staging() ? "staging.aerofs.com/sp" : "sp.aerofs.com");
    }

    @Override
    public String webAdminHost()
    {
        return "my.aerofs.com";
    }

    @Override
    public String ssHost()
    {
        return "sss.aerofs.com";
    }

    @Override
    public String mcastAddr()
    {
        return Cfg.staging() ? "225.7.8.8" : "225.7.8.9";
    }

    @Override
    public int mcastPort()
    {
        return Cfg.staging() ? 29870 : 29871;
    }

    @Override
    public String logoURL()
    {
        return "http://www.aerofs.com/img/logo.png";
    }

    @Override
    public String htmlEmailHeaderColor()
    {
        return "#17466B";
    }
}

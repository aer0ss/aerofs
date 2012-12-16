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
    @Override
    public boolean isMultiuser()
    {
        return false;
    }

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
    public String product()
    {
        return "AeroFS";
    }

    @Override
    public String productUnixName()
    {
        return "aerofs";
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

    @Override
    public int defaultPortbase()
    {
        return 50193;
    }
}

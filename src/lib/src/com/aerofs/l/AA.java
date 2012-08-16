package com.aerofs.l;

import com.aerofs.l.L.LabelingType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;

/**
 * for AeroFS
 */
public class AA implements ILabeling
{
    private final DID _spDID;

    public AA()
    {
        _spDID = new DID(Cfg.staging() ?
                "ac4c5631b47b39281c16074370b1b23d" :        // was 4321aerofs.com
                "91cee4ffb4f998e7591cf298f31ec558", true);  // was 1234aerofs.com
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
        return "x.aerofs.com";
    }

    @Override
    public short xmppServerPort()
    {
        return Cfg.staging() ? (short) 9328 : 443;
    }

    @Override
    public String jingleRelayHost()
    {
        return "97.107.139.17";
    }

    @Override
    public short jingleRelayPort()
    {
        return Cfg.staging() ? (short) 7583 : 80;
    }

    @Override
    public String spUser()
    {
        return "aerofs.com";
    }

    @Override
    public DID spDID()
    {
        return _spDID;
    }

    @Override
    public String spHost()
    {
        return "reloadedsp.aerofs.com";
    }

    @Override
    public short spPort()
    {
        return 443;
    }


    @Override
    public String svHost()
    {
        return "sp.aerofs.com";
    }

    @Override
    public short svPort()
    {
        return 443;
    }

    @Override
    public String ssHost()
    {
        return "sss.aerofs.com";
    }

    @Override
    public short ssPort()
    {
        return 443;
    }

    @Override
    public String mcastAddr()
    {
        return Cfg.staging() ? "225.7.8.8" : "225.7.8.9";
    }

    @Override
    public short mcastPort()
    {
        return Cfg.staging() ? (short) 29870 : 29871;
    }

    @Override
    public String spEndpoint()
    {
        return Cfg.staging() ?
                "sterling-staging.aerofs.com:80" :
                "sterling.aerofs.com:443";
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
    public LabelingType type()
    {
        return LabelingType.AEROFS;
    }
}

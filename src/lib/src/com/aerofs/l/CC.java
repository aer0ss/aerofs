package com.aerofs.l;

import com.aerofs.l.L.LabelingType;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;

/**
 * for Comcast
 */
public class CC implements ILabeling
{
    private final DID _spDID;

    public CC()
    {
        try {
            _spDID = new DID("ac4c5631b47b39281c16074370b1b23d");
        } catch (ExFormatError e) {
            throw SystemUtil.fatalWithReturn(e);
        }
    }

    @Override
    public int trayIconAnimationFrameCount()
    {
        return 24;
    }

    @Override
    public long trayIconAnimationFrameInterval()
    {
        return 50;
    }

    @Override
    public String product()
    {
        return "Xfinity Sync";
    }

    @Override
    public String productUnixName()
    {
        return "xsync";
    }

    @Override
    public String vendor()
    {
        return "Comcast";
    }

    @Override
    public String webHost()
    {
        return "www-comcast.aerofs.com";
    }

    @Override
    public String xmppServerAddr()
    {
        return "relay.comcast.aerofs.net";
    }

    @Override
    public short xmppServerPort()
    {
        return 443;
    }

    @Override
    public String jingleRelayHost()
    {
        return "relay.comcast.aerofs.net";
    }

    @Override
    public short jingleRelayPort()
    {
        return 80;
    }

    @Override
    public UserID spUser()
    {
        return UserID.fromInternal("aerofs.com");
    }

    @Override
    public DID spDID()
    {
        return _spDID;
    }

    @Override
    public String spUrl()
    {
        return "sp-comcast.aerofs.com";
    }

    @Override
    public short spPort()
    {
        return 443;
    }

    @Override
    public String svHost()
    {
        return "sv.aerofs.com";
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
        return "225.7.8.10";
    }

    @Override
    public short mcastPort()
    {
        return 29872;
    }

    @Override
    public String spEndpoint()
    {
        return "x1.comcast.aerofs.net:443";
    }

    @Override
    public String logoURL()
    {
        return "http://www.aerofs.com/img/aBcDEfg.png";
    }

    @Override
    public String htmlEmailHeaderColor()
    {
        return "#b9142d";
    }

    @Override
    public LabelingType type()
    {
        return LabelingType.COMCAST;
    }
}

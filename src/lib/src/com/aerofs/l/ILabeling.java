package com.aerofs.l;

import com.aerofs.l.L.LabelingType;
import com.aerofs.lib.id.DID;

/**
 * see the comments in L.java
 */
public interface ILabeling
{
    int trayIconAnimationFrameCount();

    long trayIconAnimationFrameInterval();

    String product();

    String productUnixName();

    String vendor();

    String xmppServerAddr();

    short xmppServerPort();

    String jingleRelayHost();

    short jingleRelayPort();

    String spUser();

    DID spDID();

    String webHost();

    String spUrl();

    short spPort();

    String svHost();

    short svPort();

    String ssHost();

    short ssPort();

    String mcastAddr();

    short mcastPort();

    String spEndpoint();

    String logoURL();

    String htmlEmailHeaderColor();

    LabelingType type();
}

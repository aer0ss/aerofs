package com.aerofs.lib;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;

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

    UserID spUser();

    DID spDID();

    String webHost();

    String spUrl();

    String ssHost();

    String mcastAddr();

    short mcastPort();

    String spEndpoint();

    String logoURL();

    String htmlEmailHeaderColor();
}

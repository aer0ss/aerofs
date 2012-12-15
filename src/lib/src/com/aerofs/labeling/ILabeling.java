/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling;

/**
 * See class L for details.
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

    String webHost();

    String spUrl();

    String ssHost();

    String mcastAddr();

    short mcastPort();

    ////////
    // Labeling methods specific to servers.
    // TODO (WW) move them to server packages

    String logoURL();

    String htmlEmailHeaderColor();
}

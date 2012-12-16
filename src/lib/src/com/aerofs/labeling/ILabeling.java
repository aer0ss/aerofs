/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling;

/**
 * See class L for details.
 */
public interface ILabeling
{
    boolean isMultiuser();

    int trayIconAnimationFrameCount();

    long trayIconAnimationFrameInterval();

    String product();

    String productUnixName();

    String vendor();

    String xmppServerAddr();

    int xmppServerPort();

    String jingleRelayHost();

    int jingleRelayPort();

    String webHost();

    String spUrl();

    String ssHost();

    String mcastAddr();

    int mcastPort();

    int defaultPortbase();

    ////////
    // Labeling methods specific to servers.
    // TODO (WW) move them to server packages

    String logoURL();

    String htmlEmailHeaderColor();
}

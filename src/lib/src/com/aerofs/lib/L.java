package com.aerofs.lib;

import com.aerofs.lib.labelings.ClientLabeling;

/**
 * L stands for "Labeling". Labeling is determined by the build script removing unneeded labeling
 * classes from the com.aerofs.lib.labelings package.
 */
public class L
{
    public static interface ILabeling
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

        String spEndpoint();

        ////////
        // Labeling specific to servers.
        // TODO (WW) move them to server packages

        String logoURL();

        String htmlEmailHeaderColor();
    }

    private static ILabeling s_l = new ClientLabeling();

    public static ILabeling get()
    {
        return s_l;
    }
}

package com.aerofs.lib;

import com.aerofs.lib.labelings.ClientLabeling;

/**
 * L stands for "Labeling". Labeling is determined by the build script removing unneeded labeling
 * classes from the com.aerofs.lib.labelings package.
 */
public class L
{
    private static ILabeling s_l = new ClientLabeling();

    public static ILabeling get()
    {
        return s_l;
    }
}

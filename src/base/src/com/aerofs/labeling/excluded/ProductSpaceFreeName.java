/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.labeling.excluded;

import com.aerofs.labeling.L;

/**
 * This class is called by the build script only, and is removed from the production jar.
 */
public class ProductSpaceFreeName
{
    public static void main(String[] args)
    {
        System.out.println(L.get().productSpaceFreeName());
    }
}

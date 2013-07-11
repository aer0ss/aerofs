/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling;

/**
 * See class L for details.
 */
public interface ILabeling
{
    String brand();

    boolean isMultiuser();

    String product();

    String productSpaceFreeName();

    String productUnixName();

    String rootAnchorName();

    int defaultPortbase();
}

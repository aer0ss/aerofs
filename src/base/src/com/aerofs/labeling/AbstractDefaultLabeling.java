/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.labeling;

/**
 * The default labeling class for concrete labeling classes to inherit. The default labeling
 * represents an AeroFS single-user client.
 */
public abstract class AbstractDefaultLabeling implements ILabeling
{
    // Note that for now we have chosen to only use one map file, so the mapping for classes that
    // inherit from this class might be broken. This isn't a big deal though, since you can identify
    // what function is being called using line numbers.
}

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.ex.ExNotFound;

public class ExSIDNotFound extends ExNotFound
{
    private static final long serialVersionUID = -1614532004700724139L;

    public ExSIDNotFound(String s) { super(s); }
}

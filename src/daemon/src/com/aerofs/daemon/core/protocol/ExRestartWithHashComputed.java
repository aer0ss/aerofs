/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.lib.id.KIndex;

public class ExRestartWithHashComputed extends Exception
{
    private static final long serialVersionUID = 1L;

    public final KIndex kidx;

    public ExRestartWithHashComputed(KIndex kidx) {
        this.kidx = kidx;
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SOCID;

public interface IPushUpdatesListener
{
    void receivedPushUpdate_(SOCID socid, DID didFrom);
}

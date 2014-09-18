/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

/**
 *
 */
public class MetaChangeSubmitter
{
    @Inject
    public MetaChangeSubmitter()
    {

    }

    public boolean ackMatchingSubmittedMetaChange_(SIndex sidx, RemoteChange c, RemoteLink lnk,
            Trans t)
    {
        return false;
    }
}

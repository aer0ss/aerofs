/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.admin.HdRelocateRootAnchor.SameFSRelocator;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

class LinkedSameFSRelocator extends SameFSRelocator
{
    private final LinkerRootMap _lrm;

    @Inject
    public LinkedSameFSRelocator(InjectableFile.Factory factFile, LinkerRootMap lrm)
    {
        super(factFile);
        _lrm = lrm;
    }

    @Override
    public void afterRootRelocation(Trans t) throws Exception
    {
        _lrm.move_(_sid, _oldRoot.getAbsolutePath(), _newRoot.getAbsolutePath(), t);
    }
}

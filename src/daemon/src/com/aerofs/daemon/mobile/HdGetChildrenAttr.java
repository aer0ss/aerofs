/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import static com.aerofs.daemon.core.fs.HdGetChildrenAttr.getChildrenAttrImpl_;

public class HdGetChildrenAttr extends AbstractHdIMC<EIGetChildrenAttr>
{
    private final DirectoryService _ds;
    private final LocalACL _acl;

    @Inject
    public HdGetChildrenAttr(DirectoryService ds, LocalACL acl)
    {
        _ds = ds;
        _acl = acl;
    }

    @Override
    protected void handleThrows_(EIGetChildrenAttr ev, Prio prio) throws Exception
    {
        SOID soid = _acl.checkThrows_(ev._user, ev._path);

        ev.setResult_(getChildrenAttrImpl_(soid, _ds));
    }
}

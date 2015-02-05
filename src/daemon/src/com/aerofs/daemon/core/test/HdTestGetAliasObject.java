/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.test;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.test.EITestGetAliasObject;
import com.aerofs.daemon.lib.db.IAliasDatabase;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import java.util.List;

public class HdTestGetAliasObject extends AbstractHdIMC<EITestGetAliasObject>
{
    private final DirectoryService _ds;
    private final IAliasDatabase _aldb;

    @Inject
    public HdTestGetAliasObject(DirectoryService ds, IAliasDatabase aldb)
    {
        _ds = ds;
        _aldb = aldb;
    }

    @Override
    protected void handleThrows_(EITestGetAliasObject ev, Prio prio) throws Exception
    {
        IDBIterator<OID> it = _aldb.getAliases_(_ds.resolveNullable_(ev._path));
        try {
            List<ByteString> l = Lists.newArrayList();
            while (it.next_()) l.add(BaseUtil.toPB(it.get_()));
            ev.setResult_(l);
        } finally {
            it.close_();
        }
    }
}

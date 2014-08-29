package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class HdListExpelledObjects extends AbstractHdIMC<EIListExpelledObjects>
{
    private final Expulsion _expulsion;
    private final DirectoryService _ds;

    @Inject
    public HdListExpelledObjects(Expulsion expulsion, DirectoryService ds)
    {
        _expulsion = expulsion;
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIListExpelledObjects ev, Prio prio) throws Exception
    {
        ImmutableList.Builder<Path> bdExpelled = new ImmutableList.Builder<>();

        IDBIterator<SOID> iter = _expulsion.getExpelledObjects_();
        try {
            while (iter.next_()) {
                SOID soid = iter.get_();
                Path path = _ds.resolveNullable_(soid);
                if (path == null) {
                    l.warn("invalid expulsion entry {}", soid);
                    continue;
                }
                // skip stuff in the trash
                if (path.elements().length < 1 || !path.elements()[0].equals(LibParam.TRASH)) {
                    bdExpelled.add(path);
                }
            }
        } finally {
            iter.close_();
        }

        ev.setResult_(bdExpelled.build());
    }

}

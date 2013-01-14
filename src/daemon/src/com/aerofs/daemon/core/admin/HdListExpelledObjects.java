package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.event.admin.EIListExpelledObjects;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Param;
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
        ImmutableList.Builder<Path> bdExpelled = new ImmutableList.Builder<Path>();

        IDBIterator<SOID> iter = _expulsion.getExpelledObjects_();
        try {
            while (iter.next_()) {
                Path path = _ds.resolve_(iter.get_());
                // skip stuff in the trash
                if (path.elements().length < 1 || !path.elements()[0].equals(Param.TRASH)) {
                    bdExpelled.add(path);
                }
            }
        } finally {
            iter.close_();
        }

        ev.setResult_(bdExpelled.build());
    }

}

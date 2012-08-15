package com.aerofs.daemon.core.admin;

import java.util.ArrayList;
import java.util.HashSet;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.event.admin.EIListConflicts;
import com.aerofs.daemon.event.admin.EIListConflicts.ConflictEntry;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.Path;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class HdListConflicts extends AbstractHdIMC<EIListConflicts>
{
    private final IMetaDatabase _mdb;
    private final DirectoryService _ds;

    @Inject
    public HdListConflicts(DirectoryService ds, IMetaDatabase mdb)
    {
        _ds = ds;
        _mdb = mdb;
    }

    @Override
    protected void handleThrows_(EIListConflicts ev, Prio prio) throws Exception
    {
        ArrayList<ConflictEntry> conflicts = new ArrayList<ConflictEntry>();

        IDBIterator<SOKID> iter = _mdb.getNonMasterBranches_();
        try {
            while (iter.next_()) {
                SOKID sokid = iter.get_();
                OA oa = _ds.getOA_(sokid.soid());
                Path path = _ds.resolve_(oa);

                // figure editors. TODO
                HashSet<String> editors = Sets.newHashSet();
//                Version vMaster = _cd.getLocalVersion_(new SOCKID(sokid.soid(), CID.CONTENT));
//                Version v = _cd.getLocalVersion_(new SOCKID(sokid, CID.CONTENT));
//                Version vDiff = v.sub_(vMaster);
//                for (DID did : vDiff.getAll_().keySet()) {
//                    editors.add(did.user());
//                }
                editors.add("(unknown)");

                // figure fspath
                IPhysicalFile pf = oa.ca(sokid.kidx()).physicalFile();
                String physicalPath = pf.getAbsPath_();

                conflicts.add(new ConflictEntry(path, sokid.kidx(),
                        physicalPath == null ? "" : physicalPath, editors));
            }
        } finally {
            iter.close_();
        }

        ev.setResult_(conflicts);
    }
}

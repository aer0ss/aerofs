package com.aerofs.daemon.core.admin;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.admin.EIListConflicts;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.ListConflictsReply.ConflictedPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

public class HdListConflicts extends AbstractHdIMC<EIListConflicts>
{
    private final DirectoryService _ds;

    @Inject
    public HdListConflicts(DirectoryService ds)
    {
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIListConflicts ev) throws Exception
    {
        Map<Path, Integer> conflicts = Maps.newTreeMap();

        IDBIterator<SOKID> iter = _ds.getAllNonMasterBranches_();
        try {
            while (iter.next_()) {
                SOKID sokid = iter.get_();
                OA oa = _ds.getOA_(sokid.soid());
                // objects that are pending cleanup (i.e. in logical staging area) should be ignored
                if (oa.isExpelled()) continue;
                Path path = _ds.resolve_(oa);
                Integer n = conflicts.get(path);
                conflicts.put(path, n == null ? 1 : n + 1);
            }
        } finally {
            iter.close_();
        }

        List<ConflictedPath> pathList = Lists.newArrayListWithCapacity(conflicts.size());
        for (Entry<Path, Integer> e : conflicts.entrySet()) {
            pathList.add(ConflictedPath.newBuilder()
                    .setPath(e.getKey().toPB())
                    .setBranchCount(e.getValue()).build());
        }
        ev.setResult_(pathList);
    }
}

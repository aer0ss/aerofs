package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Doing SOID->Path resolution requires walking up the OID hierarchy
 *
 * May as well keep this information around...
 */
public class ResolvedPath extends Path
{
    public ImmutableList<SOID> soids;

    public ResolvedPath(SID sid, List<SOID> oids, List<String> elems)
    {
        super(sid, elems);
        soids = ImmutableList.copyOf(oids);
        Preconditions.checkState(soids.size() == elems.size());
    }

    public SOID soid()
    {
        return soids.get(soids.size() - 1);
    }

    public ResolvedPath join(OA oa)
    {
        return join(oa.soid(), oa.name());
    }

    public ResolvedPath join(SOID soid, String name)
    {
        return new ResolvedPath(sid(),
                ImmutableList.<SOID>builder().addAll(soids).add(soid).build(),
                ImmutableList.<String>builder().addAll(Arrays.asList(elements())).add(name).build());
    }

    public ResolvedPath substituteLastSOID(SOID soid)
    {
        return new ResolvedPath(sid(),
                ImmutableList.<SOID>builder()
                        .addAll(soids.subList(0, soids.size() - 1)).add(soid).build(),
                Arrays.asList(elements()));
    }

    public static ResolvedPath root(SID sid)
    {
        return new ResolvedPath(sid, Collections.<SOID>emptyList(), Collections.<String>emptyList());
    }
}

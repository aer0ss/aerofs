package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * Doing SOID->Path resolution requires walking up the OID hierarchy
 *
 * May as well keep this information around to save ourselves the trouble of walking up
 * that hierarchy again if needed. In particular this is used by LinkedStorage to compute
 * physical path of non-representable objects.
 *
 * We keep SOIDs instead of just OIDs to also capture store boundaries.
 */
public class ResolvedPath extends Path
{
    public ImmutableList<SOID> soids;

    public ResolvedPath(SID sid, List<SOID> oids, List<String> elems)
    {
        super(sid, elems);
        soids = ImmutableList.copyOf(oids);
        checkState(soids.size() == elems.size());
    }

    /**
     * @return the SOID of the object to which this resolved path corresponds
     */
    public SOID soid()
    {
        return soids.get(soids.size() - 1);
    }

    /**
     * @return return the resolved path of a given child of the object represented by this path
     */
    public ResolvedPath join(OA oa)
    {
        return join(oa.soid(), oa.name());
    }

    /**
     * @return return the resolved path of a given child of the object represented by this path
     */
    public ResolvedPath join(SOID soid, String name)
    {
        return new ResolvedPath(sid(),
                ImmutableList.<SOID>builder().addAll(soids).add(soid).build(),
                ImmutableList.<String>builder().addAll(Arrays.asList(elements())).add(name).build());
    }

    /**
     * @return return the resolved path of the root dir of a given store
     */
    public static ResolvedPath root(SID sid)
    {
        return new ResolvedPath(sid, Collections.<SOID>emptyList(), Collections.<String>emptyList());
    }

    public ResolvedPath parent()
    {
        checkState(!isEmpty());
        return new ResolvedPath(sid(),
                soids.subList(0, soids.size() - 1),
                Arrays.asList(elements()).subList(0, soids.size() - 1));
    }

    public ResolvedPath substituteLastSOID(SOID soid)
    {
        if (isEmpty()) {
            checkState(soid.oid().isRoot());
            return this;
        }
        return new ResolvedPath(sid(),
                ImmutableList.<SOID>builder()
                        .addAll(soids.subList(0, soids.size() - 1))
                        .add(soid).build(),
                Arrays.asList(elements()));
    }

    public boolean isInTrash()
    {
        for (SOID soid : soids) if (soid.oid().isTrash()) return true;
        return false;
    }
}

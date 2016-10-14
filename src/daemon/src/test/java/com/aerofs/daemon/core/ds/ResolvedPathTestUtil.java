package com.aerofs.daemon.core.ds;

import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class ResolvedPathTestUtil
{
    public static ResolvedPath fromString(SID sid, String path)
    {
        List<String> elems = Arrays.asList(path.split("/"));
        return new ResolvedPath(sid, Lists.transform(elems,
                s -> new SOID(new SIndex(1), OID.generate())), elems);
    }

    public static ResolvedPath fromString(SID sid, String path, final SOID... soids)
    {
        List<String> elems = Arrays.asList(path.split("/"));
        return new ResolvedPath(sid, Lists.newArrayList(soids), elems);
    }
}

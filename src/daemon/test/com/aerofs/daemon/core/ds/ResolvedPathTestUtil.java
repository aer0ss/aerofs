package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class ResolvedPathTestUtil
{
    public static ResolvedPath fromString(SID sid, String path)
    {
        List<String> elems = Arrays.asList(path.split("/"));
        return new ResolvedPath(sid, Lists.transform(elems, new Function<String, SOID>() {
            @Override
            public @Nullable SOID apply(@Nullable String s)
            {
                return new SOID(new SIndex(1), OID.generate());
            }
        }), elems);
    }

    public static ResolvedPath fromString(SID sid, String path, final SOID... soids)
    {
        List<String> elems = Arrays.asList(path.split("/"));
        return new ResolvedPath(sid, Lists.newArrayList(soids), elems);
    }
}

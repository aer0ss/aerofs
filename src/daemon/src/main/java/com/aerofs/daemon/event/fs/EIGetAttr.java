package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Ritual.PBBranch.PBPeer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class EIGetAttr extends AbstractEBIMC
{
    public final Path _path;

    public @Nullable OA _oa;  // null if not found (no ExNotFound is thrown)

    // a map from all branches of the object to the list of contributors for each object
    public @Nullable Map<KIndex, PBPeer> _div;

    public EIGetAttr(IIMCExecutor imce, Path path)
    {
        super(imce);
        _path = path;
    }

    /**
     * @param oa set to null if not found
     */
    public void setResult_(@Nullable OA oa, @Nullable Map<KIndex, PBPeer> div)
    {
        _oa = oa;
        _div = div;
    }
}

package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

import javax.annotation.Nullable;

public class EIGetAttr extends AbstractEIFS
{
    public final Path _path;

    public @Nullable OA _oa;  // null if not found (no ExNotFound is thrown)

    public EIGetAttr(String user, IIMCExecutor imce, Path path)
    {
        super(user, imce);
        _path = path;
    }

    /**
     * @param oa set to null if not found
     */
    public void setResult_(@Nullable OA oa)
    {
        _oa = oa;
    }
}

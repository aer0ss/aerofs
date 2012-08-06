package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIGetAttr extends AbstractEIFS
{
    public final Path _path;

    public OA _oa;  // null if not found (no ExNotFound is thrown)

    /**
     * @param dids non-null to specify peers for remote resolution before
     * trying other peers
     */
    public EIGetAttr(String user, IIMCExecutor imce, Path path)
    {
        super(user, imce);
        _path = path;
    }

    /**
     * @param oa set to null if not found
     */
    public void setResult_(OA oa)
    {
        _oa = oa;
    }
}

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.fs.AbstractEIFS;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;

public class EIJoinSharedFolder extends AbstractEIFS
{
    public final SID _sid;
    public final Path _path;

    /**
     * Create a local anchor given the SID and name of a remote store
     * @param sid the SID of the anchored store will be the value
     * specified by this parameter.
     */
    public EIJoinSharedFolder(UserID user, Path path, SID sid)
    {
        super(user, Core.imce());
        assert sid != null;
        assert path != null;
        _sid = sid;
        _path = path;
    }

}

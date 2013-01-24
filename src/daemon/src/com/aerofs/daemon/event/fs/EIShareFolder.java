package com.aerofs.daemon.event.fs;

import java.util.Map;

import com.aerofs.daemon.core.Core;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

public class EIShareFolder extends AbstractEIFS
{
    public final Path _path;
    public final Map<UserID, Role> _subject2role;
    public final String _emailNote;

    /**
     * Convert an existing folder to an anchor. The anchor's OID is deterministically derived from
     * the OID of the existing folder, which in turn determines the SID of the anchored store. This
     * is to help conflict merging when two or more devices convert the same folder at the same
     * time.
     * @param path the path of the new store
     */
    public EIShareFolder(UserID user, Path path, Map<UserID, Role> subject2role, String emailNote)
    {
        super(user, Core.imce());
        assert path != null;
        _path = path;
        _subject2role = subject2role;
        _emailNote = emailNote;
    }
}

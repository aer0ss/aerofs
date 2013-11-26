package com.aerofs.daemon.event.fs;

import java.util.Map;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

public class EIShareFolder extends AbstractEBIMC
{
    public final Path _path;
    public final Map<UserID, Permissions> _subject2role;
    public final String _emailNote;
    public final boolean _suppressSharedFolderRulesWarnings;

    /**
     * Convert an existing folder to an anchor. The anchor's OID is deterministically derived from
     * the OID of the existing folder, which in turn determines the SID of the anchored store. This
     * is to help conflict merging when two or more devices convert the same folder at the same
     * time.
     * @param path the path of the new store
     */
    public EIShareFolder(Path path, Map<UserID, Permissions> subject2role, String emailNote,
            boolean suppressSharedFolderRulesWarnings)
    {
        super(Core.imce());
        assert path != null;
        _path = path;
        _subject2role = subject2role;
        _emailNote = emailNote;
        _suppressSharedFolderRulesWarnings = suppressSharedFolderRulesWarnings;
    }
}

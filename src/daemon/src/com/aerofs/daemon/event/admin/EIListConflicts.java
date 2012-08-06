package com.aerofs.daemon.event.admin;

import java.util.List;
import java.util.Set;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.Path;

public class EIListConflicts extends AbstractEBIMC
{

    public EIListConflicts()
    {
        super(Core.imce());
    }

    public static class ConflictEntry {
        public final Path _path;
        public final KIndex _kidx;
        public final String _fspath;
        public final Set<String> _editors;

        public ConflictEntry(Path path, KIndex kidx, String fspath,
                Set<String> editors)
        {
            _path = path;
            _kidx = kidx;
            _fspath = fspath;
            _editors = editors;
        }
    }

    public List<ConflictEntry> _conflicts;

    public void setResult_(List<ConflictEntry> conflicts)
    {
        _conflicts = conflicts;
    }
}

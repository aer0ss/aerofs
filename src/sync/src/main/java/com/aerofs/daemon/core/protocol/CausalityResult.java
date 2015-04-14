package com.aerofs.daemon.core.protocol;

import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.KIndex;
import com.google.common.base.Joiner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public class CausalityResult {
    // the kidx to which the downloaded update will be applied
    public final KIndex _kidx;
    // the version vector to be added to the branch corresponding to kidxApply
    public final Version _vAddLocal;
    // the branches to be deleted. never be null
    public final Collection<KIndex> _kidcsDel;
    // if content I/O should be avoided
    public final boolean _avoidContentIO;

    @Nullable
    public final ContentHash _hash;

    public final Version _vLocal;

    public CausalityResult(@Nonnull KIndex kidx, @Nonnull Version vAddLocal,
                           @Nonnull Collection<KIndex> kidcsDel,
                           @Nullable ContentHash h, @Nonnull Version vLocal, boolean avoidContentIO) {
        _kidx = kidx;
        _vAddLocal = vAddLocal;
        _kidcsDel = kidcsDel;
        _hash = h;
        _vLocal = vLocal;
        _avoidContentIO = avoidContentIO;
    }

    @Override
    public String toString() {
        return Joiner.on(' ').useForNull("null")
                .join(_kidx, _vAddLocal, _kidcsDel, _hash, _vLocal);
    }
}

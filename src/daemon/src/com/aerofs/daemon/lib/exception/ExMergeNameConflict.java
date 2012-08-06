package com.aerofs.daemon.lib.exception;

import com.aerofs.lib.id.SOID;

public class ExMergeNameConflict extends Exception {
    private static final long serialVersionUID = 1L;
    public final SOID _soid1;
    public final SOID _soid2;

    public ExMergeNameConflict(SOID soid1, SOID soid2)
    {
        _soid1 = soid1;
        _soid2 = soid2;
    }
}

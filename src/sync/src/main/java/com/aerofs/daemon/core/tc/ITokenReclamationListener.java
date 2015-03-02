package com.aerofs.daemon.core.tc;

import javax.annotation.Nonnull;

public interface ITokenReclamationListener
{
    /**
     * Only one listener, which is the first registered listener, is called on
     * each reclamation. The caller unregisters the listener before calling
     * the method.
     *
     * The listener MUST call {@paramref cascade} unless is acquires at least
     * one token of the same category. Otherwise we may end up with a bunch of
     * listeners waiting despite tokens being available.
     */
    void tokenReclaimed_(@Nonnull Runnable cascade);
}

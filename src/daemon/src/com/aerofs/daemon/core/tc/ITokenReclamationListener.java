package com.aerofs.daemon.core.tc;

public interface ITokenReclamationListener
{
    /**
     * Only one listener, which is the first registered listener, is called on
     * each reclamation. The caller unregisters the listener before calling
     * the method.
     */
    void tokenReclaimed_(Cat cat);
}

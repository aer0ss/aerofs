package com.aerofs.daemon.event.net;

import java.net.NetworkInterface;
import java.util.Set;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

// the event is guaranteed to fire every time the system starts.
// previous + added - removed == current

// N.B. the same interface may be added/remove several times in a row, due to
// retrial for other transports.

public class EOLinkStateChanged extends AbstractEBIMC
{

    public final Set<NetworkInterface> _prev, _current, _added, _removed;

    public EOLinkStateChanged(IIMCExecutor imce, Set<NetworkInterface> prev,
            Set<NetworkInterface> cur, Set<NetworkInterface> added,
            Set<NetworkInterface> removed)
    {
        super(imce);
        _prev = prev;
        _current = cur;
        _added = added;
        _removed = removed;
    }
}

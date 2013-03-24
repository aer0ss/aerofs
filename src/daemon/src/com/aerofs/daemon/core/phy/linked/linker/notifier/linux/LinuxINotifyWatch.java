package com.aerofs.daemon.core.phy.linked.linker.notifier.linux;

import com.google.common.collect.Lists;

import java.util.List;

public class LinuxINotifyWatch
{
    public final int _watchId;
    public int _parentWatchId; // Watches are bound to inodes and can get moved around.
    public String _name;       // As a result, we make these fields mutable, so we can avoid trying
                               // to register and unregister watches all the time.
                               // The only class that should touch the inside of a LinuxINotifyWatch
                               // is LinuxNotifier, and anything there that does so should be
                               // synchronized(this)
    public final List<LinuxINotifyWatch> _children;
    public LinuxINotifyWatch(int watchId, String name, int parentWatchId) {
        this._watchId = watchId;
        this._name = name;
        this._parentWatchId = parentWatchId;
        _children = Lists.newArrayList();
    }
}

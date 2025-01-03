package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.FileSystemProber.FileSystemProperty;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.slf4j.Logger;

class HdMightCreateNotification implements IEventHandler<EIMightCreateNotification> {
    private static final Logger l = Loggers.getLogger(HdMightCreateNotification.class);

    private final InjectableFile.Factory _factFile;

    private boolean _disabled;

    @Inject
    public HdMightCreateNotification(InjectableFile.Factory f) {
        _factFile = f;
    }

    @Override
    public void handle_(EIMightCreateNotification ev)
    {
        if (_disabled) return;

        /**
         * In case of consecutive renames if the notifier does not coalesce events the intermediate
         * steps would normally be ignored as the intermediate file no longer exists by the time
         * the notification is received.
         *
         * Unfortunately this breaks down for case-only renames on case-insensitive filesystems.
         * Failure to drop all but the latest notification leads to the generation of spurious
         * transforms. With a single device this is a mild annoyance. However with multiple devices
         * it turns into an exponentially amplifying loop of renames, with each device generating
         * fresh new transforms for every transform generated by every other device.
         *
         * To avoid this, check that the file exists with the right case.
         */
        if (ev._root.properties().contains(FileSystemProperty.CaseInsensitive)
                && !existsCaseSensitive(ev._absPath)) {
            l.warn("spurious notif {}", ev._absPath);
            return;
        }
        ev._root.mightCreate_(ev._absPath, ev._rescan);
    }

    private boolean existsCaseSensitive(String absPath) {
        InjectableFile f = _factFile.create(absPath);
        if (!f.exists()) return false;
        InjectableFile p = f.getParentFile();
        String[] siblings = p.list();
        if (siblings == null) return false;
        String n = f.getName();
        for (String c : siblings) {
            if (c.equals(n)) return true;
        }
        return false;
    }

    public void setDisabled(boolean disabled)
    {
        _disabled = disabled;
    }
}

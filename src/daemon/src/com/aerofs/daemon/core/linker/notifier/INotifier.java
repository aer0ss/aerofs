package com.aerofs.daemon.core.linker.notifier;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.linker.notifier.linux.LinuxNotifier;
import com.aerofs.daemon.core.linker.notifier.osx.OSXNotifier;
import com.aerofs.daemon.core.linker.notifier.windows.WindowsNotifier;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.aerofs.lib.os.OSUtil;
import com.google.inject.Inject;
import net.contentobjects.jnotify.JNotifyException;

import javax.annotation.Nonnull;

public interface INotifier
{
    public void start_() throws JNotifyException;

    public static class Factory
    {
        private final CoreQueue _cq;
        private final ScanSessionQueue _ssq;
        private final InjectableJNotify _jn;
        private final CfgAbsRootAnchor _cfgAbsRootAnchor;

        @Inject
        public Factory(CoreQueue cq, ScanSessionQueue ssq, InjectableJNotify jn,
                CfgAbsRootAnchor cfgAbsRootAnchor)
        {
            _cq = cq;
            _ssq = ssq;
            _jn = jn;
            _cfgAbsRootAnchor = cfgAbsRootAnchor;
        }

        public @Nonnull INotifier create()
        {
            switch (OSUtil.get().getOSFamily()) {
            case OSX:
                return new OSXNotifier(_ssq, _cq, _jn, _cfgAbsRootAnchor);
            case WINDOWS:
                return new WindowsNotifier(_cq, _jn, _cfgAbsRootAnchor);
            case LINUX:
                return new LinuxNotifier(_cq, _jn, _cfgAbsRootAnchor);
            default:
                Util.fatal("shouldn't get here");
                return null;
            }
        }
    }
}

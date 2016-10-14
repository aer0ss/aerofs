package com.aerofs.daemon.core.phy.linked.linker.notifier;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.notifier.linux.LinuxNotifier;
import com.aerofs.daemon.core.phy.linked.linker.notifier.osx.OSXNotifier;
import com.aerofs.daemon.core.phy.linked.linker.notifier.windows.WindowsNotifier;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.aerofs.lib.os.OSUtil;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.io.IOException;

public interface INotifier
{
    public void start_() throws IOException;

    public int addRootWatch_(LinkerRoot root) throws IOException;

    public void removeRootWatch_(LinkerRoot root) throws IOException;

    public static class Factory
    {
        private final CoreQueue _cq;
        private final InjectableJNotify _jn;

        @Inject
        public Factory(CoreQueue cq, InjectableJNotify jn)
        {
            _cq = cq;
            _jn = jn;
        }

        public @Nonnull INotifier create()
        {
            switch (OSUtil.get().getOSFamily()) {
            case OSX:
                return new OSXNotifier(_cq, _jn);
            case WINDOWS:
                return new WindowsNotifier(_cq, _jn);
            case LINUX:
                return new LinuxNotifier(_cq, _jn);
            default:
                SystemUtil.fatal("shouldn't get here");
                return null;
            }
        }
    }
}

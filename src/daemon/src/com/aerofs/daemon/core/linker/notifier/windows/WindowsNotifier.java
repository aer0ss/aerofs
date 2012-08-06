package com.aerofs.daemon.core.linker.notifier.windows;

import javax.annotation.Nullable;

import org.apache.log4j.Logger;

import com.aerofs.daemon.core.linker.Linker;
import com.aerofs.daemon.core.linker.PathCombo;
import com.aerofs.daemon.core.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.linker.notifier.INotifier;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableJNotify;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

public class WindowsNotifier implements INotifier, JNotifyListener
{
    private static Logger l = Util.l(WindowsNotifier.class);

    private final CoreQueue _cq;
    private final InjectableJNotify _jn;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;

    public WindowsNotifier(CoreQueue cq, InjectableJNotify jn, CfgAbsRootAnchor cfgAbsRootAnchor)
    {
        _cq = cq;
        _jn = jn;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
    }

    @Override
    public void start_() throws JNotifyException
    {
        _jn.addWatch(_cfgAbsRootAnchor.get(), JNotify.FILE_ANY, true, this);
    }

    @Override
    public void fileCreated(int wd, String root, String name)
    {
        log("create", name, null);
        mightCreate(root, name);
    }

    @Override
    public void fileDeleted(int wd, String root, String name)
    {
        log("delete", name, null);
        mightDelete(root, name);
    }

    @Override
    public void fileModified(int wd, String root, String name)
    {
        log("modify", name, null);
        mightCreate(root, name);
    }

    @Override
    public void fileRenamed(int wd, String root, String from, String to)
    {
        log("rename", from, to);
        mightDelete(root, from);
        mightCreate(root, to);
    }

    private static void log(String operation, String name, @Nullable String to)
    {
        if (l.isInfoEnabled()) {
            String logStrTo = to == null ? "" : (" -> " + PathCombo.toLogString(to));
            l.info(operation + " " + PathCombo.toLogString(name) + logStrTo);
        }
    }

    private void mightCreate(String root, String name)
    {
        // TODO from Weihan: Composing a full path and decomposing it in PathCombo is a bit
        // inefficient. It would be better to avoid it by changing the interface of EIMight* and
        // PathCombo.
        _cq.enqueueBlocking(new EIMightCreateNotification(Util.join(root, name)), Linker.PRIO);
    }

    private void mightDelete(String root, String name)
    {
        // TODO see above
        _cq.enqueueBlocking(new EIMightDeleteNotification(Util.join(root, name)), Linker.PRIO);
    }
}

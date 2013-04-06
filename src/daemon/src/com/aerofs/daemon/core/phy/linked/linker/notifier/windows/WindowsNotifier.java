package com.aerofs.daemon.core.phy.linked.linker.notifier.windows;

import javax.annotation.Nullable;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.lib.Util;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import com.aerofs.daemon.core.phy.linked.linker.Linker;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.phy.linked.linker.notifier.INotifier;
import com.aerofs.lib.injectable.InjectableJNotify;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

import java.util.Map;

public class WindowsNotifier implements INotifier, JNotifyListener
{
    private static Logger l = Loggers.getLogger(WindowsNotifier.class);

    private final CoreQueue _cq;
    private final InjectableJNotify _jn;

    private final Map<Integer, LinkerRoot> _id2root = Maps.newConcurrentMap();

    public WindowsNotifier(CoreQueue cq, InjectableJNotify jn)
    {
        _cq = cq;
        _jn = jn;
    }

    @Override
    public void start_() throws JNotifyException
    {
    }

    @Override
    public int addRootWatch_(LinkerRoot root) throws JNotifyException
    {
        int id = _jn.addWatch(root.absRootAnchor(), JNotify.FILE_ANY, true, this);
        _id2root.put(id, root);
        return id;
    }

    @Override
    public void removeRootWatch_(LinkerRoot root) throws JNotifyException
    {
        int id = root.watchId();
        _jn.removeWatch(id);
        _id2root.remove(id);
    }

    @Override
    public void fileCreated(int wd, String root, String name)
    {
        log("create", name, null);
        if (!Linker.isInternalPath(name)) mightCreate(wd, root, name);
    }

    @Override
    public void fileDeleted(int wd, String root, String name)
    {
        log("delete", name, null);
        if (!Linker.isInternalPath(name)) mightDelete(wd, root, name);
    }

    @Override
    public void fileModified(int wd, String root, String name)
    {
        log("modify", name, null);
        if (!Linker.isInternalPath(name)) mightCreate(wd, root, name);
    }

    @Override
    public void fileRenamed(int wd, String root, String from, String to)
    {
        log("rename", from, to);

        if (!Linker.isInternalPath(from)) mightDelete(wd, root, from);
        if (!Linker.isInternalPath(to)) mightCreate(wd, root, to);
    }

    private static void log(String operation, String name, @Nullable String to)
    {
        if (l.isDebugEnabled()) {
            String logStrTo = to == null ? "" : (" -> " + ObfuscatingFormatters.obfuscatePath(to));
            l.debug(operation + " " + ObfuscatingFormatters.obfuscatePath(name) + logStrTo);
        }
    }

    private void mightCreate(int wd, String root, String name)
    {
        _cq.enqueueBlocking(new EIMightCreateNotification(_id2root.get(wd), Util.join(root, name)),
                Linker.PRIO);
    }

    private void mightDelete(int wd, String root, String name)
    {
        _cq.enqueueBlocking(new EIMightDeleteNotification(_id2root.get(wd), Util.join(root, name)),
                Linker.PRIO);
    }
}
